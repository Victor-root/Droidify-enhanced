package com.looker.droidify.service

import android.content.Context
import androidx.annotation.StringRes
import com.looker.droidify.data.encryption.sha256
import com.looker.droidify.data.model.hex
import com.looker.droidify.model.Release
import com.looker.droidify.network.validation.FileValidator
import com.looker.droidify.network.validation.ValidationResult
import com.looker.droidify.utility.common.extension.calculateHash
import com.looker.droidify.utility.common.extension.getPackageArchiveInfoCompat
import com.looker.droidify.utility.common.extension.singleSignature
import com.looker.droidify.utility.common.extension.versionCodeCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.looker.droidify.R.string as strings

class ReleaseFileValidator(
    private val context: Context,
    private val packageName: String,
    private val release: Release,
) : FileValidator {

    // Hashing the (potentially very large) APK and parsing its manifest are both expensive and
    // blocking, so they must never run on the caller's thread (the download service drives this
    // from the main thread). Offload to a background dispatcher to avoid ANRs on big apps.
    override suspend fun validate(file: File): ValidationResult = withContext(Dispatchers.IO) {
        val checksum = sha256(file).hex()
        if (!checksum.equals(release.hash, ignoreCase = true)) {
            return@withContext invalid(strings.integrity_check_error_DESC)
        }
        val packageInfo = context.packageManager.getPackageArchiveInfoCompat(file.path)
            ?: return@withContext invalid(strings.file_format_error_DESC)
        if (packageInfo.packageName != packageName ||
            packageInfo.versionCodeCompat != release.versionCode
        ) {
            return@withContext invalid(strings.invalid_metadata_error_DESC)
        }

        packageInfo.singleSignature
            ?.calculateHash()
            ?.takeIf { it.isNotBlank() || it == release.signature }
            ?: return@withContext invalid(strings.invalid_signature_error_DESC)

        val permissions = packageInfo.permissions
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        if (!release.permissions.containsAll(permissions)) {
            return@withContext invalid(strings.invalid_permissions_error_DESC)
        }
        ValidationResult.Valid
    }

    private fun invalid(@StringRes id: Int): ValidationResult.Invalid =
        ValidationResult.Invalid(context.getString(id))
}

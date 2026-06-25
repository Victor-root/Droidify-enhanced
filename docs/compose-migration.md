# Plan de migration vers Jetpack Compose — Droidify Enhanced

> Objectif : migrer **toute** l'app de l'ancien système (Android Views + XML)
> vers Jetpack Compose, puis **supprimer** l'ancien système. Fait de façon
> **incrémentale** : chaque phase compile, est testable, et est validée avant
> de passer à la suivante.

## État des lieux (audit du code existant)

L'app tourne désormais **entièrement sur Compose** (`MainComposeActivity` est le
lanceur **et** le gestionnaire de deeplinks). L'**ancienne interface Views a été
supprimée** (`MainActivity`, tout `ui/`, `widget/`, `CursorOwner`). Il reste à
unifier la **couche données** : une ancienne base + une ancienne synchro
héritées sont encore utilisées par la tuyauterie (sync / install / prefs) en
parallèle de la nouvelle base Room.

| Zone | État Compose | Manques principaux |
|------|--------------|--------------------|
| Réglages | ✅ Fini (déjà actif) | — (sélecteur de couleur déjà ajouté) |
| Dépôts (liste/détail/édition) | 🟡 ~80% | bouton "Ajouter un dépôt", choix de miroir, collage presse-papier, nb d'apps |
| Accueil / navigation d'apps | 🟢 ~80% | filtre par dépôt, bouton "tout mettre à jour" (onglets ✅, recherche/tri ✅, états vides ✅, refresh auto après synchro ✅) |
| Détail d'app | 🟡 ~75% | menus de la barre, description déroulante, anti-fonctionnalités (installer/MàJ/lancer/désinstaller ✅, favori ✅, changelog ✅, permissions ✅, liens ✅, captures plein écran ✅) |
| Favoris | ❌ Absent | écran dédié à créer |
| Dialogues (permissions, incompatibilité) | ❌ Absent | à porter |
| Coquille (`MainComposeActivity`) | 🟢 ~85% | reste : pré-remplissage deeplink (recherche/adresse) + intents internes notifs (install/MàJ) ; flux install ✅, deeplinks externes ✅, couleur ✅, recréation thème ✅ |

## Feuille de route

### Phase 0 — Fondations (la coquille Compose devient capable)
- [x] Porter le thème + couleur d'accentuation (DynamicColors) dans `MainComposeActivity`
- [x] Faire de `MainComposeActivity` le lanceur de l'app (l'app ouvre directement Compose)
- [x] Brancher le flux d'installation/désinstallation (`InstallManager`)
- [~] Gérer les intents/deeplinks : **voir une app** ✅ (lien f-droid / `market://` / `fdroid.app` → fiche Compose), **ajouter un dépôt** (`fdroidrepo://`) → formulaire, recherche → accueil. Les filtres d'intent sont passés de `MainActivity` à `MainComposeActivity`. Reste : pré-remplir l'adresse/la recherche, et les intents **internes** des notifs (install/MàJ) — à finir en supprimant `MainActivity`.
- **Testable :** lancer l'app Compose, vérifier thème/couleur, et que les liens externes ouvrent le bon écran.

### Phase 1 — Accueil / navigation (+ redesign)
- [x] **Onglets Disponibles / Installées / Mises à jour** — l'onglet MàJ affiche un compteur ; détection des mises à jour en comparant la version installée à la dernière version dispo (versionCode)
- [x] Recherche + **tri** (menu déroulant fonctionnel, persiste le choix) + synchro (+ barre de progression)
- [ ] Filtre par dépôt
- [~] États vides (onglets Installées/MàJ ✅), bouton "remonter en haut", "tout mettre à jour"
- [x] **Rafraîchir après une synchro** : la liste, le carrousel « What's new » et le compteur de mises à jour se mettent à jour **tout seuls** après une synchro (et après une install/désinstall), via un signal réactif Room sur les tables `app`/`version`. Plus besoin de changer un filtre pour voir les nouveautés.
- [~] Redesign **accueil "magasin" unifié** : 1re vitrine en place (carrousel « What's new » / nouveautés en haut de l'accueil). Reste : section « mises à jour dispo », catégories mises en avant.
- **Testable :** parcourir, chercher, trier, synchroniser depuis l'accueil Compose.

### Phase 2 — Détail d'app (parité complète)
- [x] **2a** — Bouton d'action (états Installer/MàJ/Lancer/Désinstaller/Annuler) + **Lancer** + **Désinstaller** — ✅ validé sur téléphone
- [x] **2b** — **Téléchargement + installation** (le bouton télécharge, vérifie le hash SHA-256, puis installe) — branché directement via `Downloader` + `InstallManager` (sans passer par `DownloadService`). **Vraie barre de progression** : Mo téléchargés / total, vitesse en Mo/s et %, bouton Annuler. Manque : téléchargement en arrière-plan (notification) + reprise.
- [ ] Actions de la barre (partager, source, infos, désinstaller)
- [x] Sections : **changelog (What's new)**, **permissions** (dépliable, avec compteur), **liens** (site / source / suivi de bugs / changelog / traduction / dons / site de l'auteur) — tout depuis les données déjà chargées
- [x] Captures en **plein écran** : visionneuse balayable (HorizontalPager, fond noir, bouton fermer)
- [ ] Sections restantes : vidéo, description **déroulante**, anti-fonctionnalités (données pas encore peuplées côté data), liste des versions (déjà affichée en brut)
- [x] **Bouton favori** (persiste dans les réglages)
- [ ] Interrupteurs "ignorer les mises à jour"
- **➡️ Compose est déjà le lanceur** (bascule faite en phase 0) ; cette phase le rend pleinement utilisable (parcourir + installer).
- **Testable :** installer/mettre à jour/lancer de vraies apps depuis Compose.

### Phase 3 — Dépôts + Favoris
- [ ] Bouton "Ajouter un dépôt", dialogue de miroir, collage presse-papier, nb d'apps
- [ ] Écran Favoris dédié + entrée de navigation

### Phase 4 — Dialogues & flux restants
- [ ] Dialogue des permissions, dialogues d'incompatibilité, confirmations (en Compose)
- [ ] Deeplink de recherche + cas limites d'intents

### Phase 5 — Couleur du thème, étapes 2 & 3
- [ ] Onglet "Personnalisé" (couleur libre)
- [ ] Onglet "Icône" (couleur de l'icône de l'app)
- [ ] Picker à onglets conforme au screenshot

### Phase 6 — Suppression de l'ancien système
- [x] **Supprimer l'ancienne UI Views** : `MainActivity`, tout `ui/` (21 fichiers), `widget/` (5), `CursorOwner`, l'extension `Fragment.mainActivity` — supprimés ; entrée manifest retirée ; notifs + deeplinks repointés sur Compose.
- [ ] Supprimer les **layouts XML** (21) + ressources héritées (menus / animators / styles) devenues inutilisées
- [ ] **Unifier la couche données** : faire passer sync / install / `ProductPreferences` / export-import de dépôts sur la base **Room**, puis supprimer l'ancienne `database/` + l'ancienne synchro (`SyncService`, `RepositoryUpdater`)
- [ ] Nettoyage final + élagage des dépendances inutilisées

## Stratégie (choix du mainteneur)
L'app bascule **immédiatement** sur l'interface Compose : une seule app, une seule
icône. Pendant la migration l'app est donc **incomplète** (notamment, l'installation
d'apps ne marche qu'à partir de la phase 2) — c'est assumé. L'ancienne `MainActivity`
(Views) est conservée seulement pour ses deeplinks, jusqu'à leur migration.

## Bugs connus (à corriger)
- **Synchro gourmande en mémoire** : l'analyse de l'index v2 alloue ~200 Mo
  d'un coup → `OutOfMemoryError` lors d'un vrai parse d'index (repéré dans le
  logcat en testant le bouton Sync). L'app survit (erreur rattrapée) et la
  synchro fonctionne quand l'index est inchangé (cache), mais une grosse mise à
  jour d'index échouera. Correctif prévu : parser l'index en streaming plutôt
  que de tout charger en mémoire. (Code hérité, couche data/sync v2.)

## Notes techniques (pièges rencontrés)
- **Nom de fichier APK avec slash en tête** : dans l'index **v2**, les noms de
  fichiers commencent par `/` (ex. `/An.stop_10.apk`). Pour construire l'URL de
  téléchargement il faut **concaténer** `repo.address` + le nom (comme le fait
  `sync/v2/EntrySyncable.kt`), surtout **pas** `Uri.appendPath()` qui encoderait
  le `/` en `%2F` → le serveur renvoie une erreur et l'install échoue. (Bug
  corrigé dans `AppDetailViewModel.downloadAndInstall`.)

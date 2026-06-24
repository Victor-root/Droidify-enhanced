# Plan de migration vers Jetpack Compose — Droidify Enhanced

> Objectif : migrer **toute** l'app de l'ancien système (Android Views + XML)
> vers Jetpack Compose, puis **supprimer** l'ancien système. Fait de façon
> **incrémentale** : chaque phase compile, est testable, et est validée avant
> de passer à la suivante.

## État des lieux (audit du code existant)

Les développeurs d'origine ont commencé une réécriture Compose en parallèle
(`compose/` + `MainComposeActivity` avec une navigation complète), mais elle
n'est **ni terminée ni activée** : l'app livrée tourne encore entièrement sur
l'ancien système (`MainActivity` + Fragments).

| Zone | État Compose | Manques principaux |
|------|--------------|--------------------|
| Réglages | ✅ Fini (déjà actif) | — (sélecteur de couleur déjà ajouté) |
| Dépôts (liste/détail/édition) | 🟡 ~80% | bouton "Ajouter un dépôt", choix de miroir, collage presse-papier, nb d'apps |
| Accueil / navigation d'apps | 🟠 ~40% | onglets (Dispo/Installées/MàJ), recherche, tri, synchro, filtre par dépôt, états vides |
| Détail d'app | 🔴 ~20% | bouton Installer/MàJ/Lancer, progression, menus, permissions, liens, changelog, dons, anti-fonctionnalités |
| Favoris | ❌ Absent | écran dédié à créer |
| Dialogues (permissions, incompatibilité) | ❌ Absent | à porter |
| Coquille (`MainComposeActivity`) | 🔴 Incomplète | **flux d'installation**, **deeplinks/intents**, **couleur d'accentuation**, recréation au changement de thème |

## Feuille de route

### Phase 0 — Fondations (la coquille Compose devient capable)
- [x] Porter le thème + couleur d'accentuation (DynamicColors) dans `MainComposeActivity`
- [x] Faire de `MainComposeActivity` le lanceur de l'app (l'app ouvre directement Compose)
- [ ] Brancher le flux d'installation/désinstallation (`InstallManager`)
- [ ] Gérer les intents/deeplinks (installer, voir une app, recherche, ajouter un dépôt)
- **Testable :** lancer l'app Compose, vérifier thème/couleur, et que les liens externes ouvrent le bon écran.

### Phase 1 — Accueil / navigation (+ redesign)
- [ ] Onglets Disponibles / Installées / Mises à jour
- [ ] Recherche, tri, synchro (+ barre de progression), filtre par dépôt
- [ ] États vides/chargement, bouton "remonter en haut", "tout mettre à jour"
- [ ] Redesign — **direction choisie : accueil "magasin" unifié** (sections en vitrine : nouveautés, mises à jour dispo, catégories mises en avant ; recherche proéminente)
- **Testable :** parcourir, chercher, trier, synchroniser depuis l'accueil Compose.

### Phase 2 — Détail d'app (parité complète)
- [ ] **2a** — Bouton d'action (états Installer/MàJ/Lancer/Désinstaller/Annuler) + **Lancer** + **Désinstaller** (sans téléchargement, plus simple)
- [ ] **2b** — **Téléchargement + installation** (le bouton télécharge puis installe ; barre de progression) — défi : le téléchargeur est un service lié (`DownloadService`) à brancher proprement dans Compose
- [ ] Actions de la barre (partager, source, infos, désinstaller)
- [ ] Sections : captures (+ vidéo + plein écran), description (déroulante), changelog, anti-fonctionnalités, permissions, liens, dons, liste des versions
- [ ] Bouton favori, interrupteurs "ignorer les mises à jour"
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
- [ ] Supprimer les Fragments `ui/`, adaptateurs, layouts XML, anciens styles/thème, `MainActivity`, viewBinding
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

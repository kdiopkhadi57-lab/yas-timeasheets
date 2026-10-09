# YAS IT-FACTORY · Fiches de temps

Fin de l'Excel refait à la main chaque vendredi et chaque fin de mois.
Chaque consultant note ce qu'il fait au fil des jours ; l'application en tire :

- **le rapport hebdomadaire** (du **samedi au vendredi** : le week-end qui précède le lundi, puis la semaine), envoyé au **manager** du consultant, les managers au-dessus de lui en copie ;
- **la fiche de temps mensuelle** (du **20 au 19**), envoyée au **Head of Office**, la chaîne des managers en copie.

Les deux fichiers sont générés **au format exact de la fiche YAS**
(`Prenom NOM - C099 - Fiche de temps Mai - Juin 2026.xlsx`) : en-tête Prénom(s) & Nom / Matricule / Profil / Période,
une ligne par jour `Date | Projet | Business Unit | Liste des tâches | Commentaire`, cadre « Signature Consultant ».
Un second onglet « Suivi des tâches » liste les tâches ouvertes et terminées sur la période.

## Fonctionnement

| Qui | Fait quoi |
| --- | --- |
| Consultant / architecte | Saisit chaque jour ce qu'il a fait (lié ou non à une tâche assignée), met à jour le statut de ses tâches, soumet son rapport hebdo et sa fiche mensuelle |

Pour l'instant, pas de tâches assignées aux **développeurs et lead devs** : après le daily sur Teams, chacun sait ce qu'il
a à faire et note chaque jour dans « Ma semaine » le projet et ce qu'il a fait. Tout ce qui concerne les tâches leur est masqué :
menu « Mes tâches » (et la page elle-même), panneau et champ « Tâche » de la saisie, chiffres et graphiques de tâches du
estimations de « Mes projets », onglet « Suivi des tâches » de l'Excel.
Pour réactiver les tâches : règle `usesTasks` dans `frontend/src/app/core/auth.service.ts` et `Role.usesTasks()` côté backend.
| Manager (rôle `PM`) | Crée et assigne les tâches, suit son équipe (soumis / manquants), valide ou renvoie les rapports hebdo, télécharge le ZIP de l'équipe |
| Head of Office | Voit tous les consultants, valide les fiches mensuelles, télécharge le ZIP de toutes les fiches + synthèse |
| Admin | Gère utilisateurs (matricule, profil, manager), projets et Business Units |

## Hiérarchie des managers

Chaque développeur est rattaché à un **manager** (ex. Demba FALL). Un manager peut lui-même avoir un manager
(ex. Mouhamadou Moustapha BITEYE) : il voit alors les fiches de toute l'équipe en dessous de lui, peut les valider,
et reçoit en copie les rapports hebdo. Les **architectes** sont rattachés au **Head of Office** (Toussaint BASSENE).
Pas de boucle possible dans la hiérarchie.

Un **manager crée les développeurs de son équipe** (Administration › « ＋ Nouveau développeur ») et modifie ceux
qui sont sous sa responsabilité : rôle Consultant ou Lead dev, rattachés à lui ou à un manager sous lui.
Chaque développeur a son **profil** (Backend, Frontend, Fullstack), ses **technologies** (Java, PHP, Talend, Python)
et ses **projets** : cocher un projet l'ajoute à l'équipe du projet comme développeur (il est prévenu par e-mail).
L'Admin et le Head of Office gèrent tous les comptes.

| Rapport | Destinataire | En copie |
| --- | --- | --- |
| Hebdo | Manager direct (Head of Office à défaut) | Managers au-dessus, consultant |
| Mensuel | Head of Office | Toute la chaîne des managers, consultant |

## Notifications par e-mail

Chaque action est notifiée aux personnes concernées (jamais à son auteur) :
soumission, retrait, validation et renvoi d'un rapport (chaîne des managers en copie) ;
création, modification, changement de statut, réassignation, suppression, estimation et commentaire d'une tâche
(assigné, créateur, lead dev, manager direct) ; dépôt, discussion et suppression de documents ;
création de compte, changement de manager / rôle / mot de passe, ajout à l'équipe d'un projet ;
envoi, validation et renvoi des factures ; relances et synthèses automatiques.
Les saisies journalières ne déclenchent pas d'e-mail : elles partent ensemble à la soumission du rapport.

## Factures mensuelles

Menu **Ma facture** : chaque consultant établit sa facture du mois au format du modèle YAS
(`Facture_CONST-IT-C060-09-2026.pdf`).

- Le consultant saisit seulement ses **honoraires nets** (et, le cas échéant, la retenue ordinateur à reverser à STAM) ;
  le **numéro** (`CONST-IT-{matricule}-{MM}-{AAAA}`), la **période** (« du 01 au 30 Septembre 2026 », dernier jour du mois
  calculé), la **date** (du jour, modifiable), les **honoraires bruts** (net ÷ 0,95) et la **retenue de 5 %** sont calculés.
  L'intitulé et le montant sont repris de la facture précédente.
- Ses coordonnées (téléphone, e-mail, adresse) et sa **signature** (dessinée à l'écran ou image importée) sont enregistrées
  une fois et apposées sur chaque facture.
- « Envoyer au Head of Office » : le PDF signé part par e-mail au(x) Head of Office (consultant en copie) et la facture est figée.
- Le Head of Office (menu **Factures**) voit chaque consultant pour le mois (envoyée, à corriger, validée, manquante),
  ouvre le PDF, **valide** ou **renvoie** avec un commentaire ; le consultant corrige puis renvoie.

| Variable | Rôle |
| --- | --- |
| `INVOICE_CLIENT` | Client facturé, `SAGA AFRICA HOLDINGS LIMITED SA` par défaut |
| `INVOICE_PREFIX` | Préfixe du numéro, `CONST-IT` par défaut |
| `INVOICE_WITHHOLDING_RATE` | Retenue sur les honoraires bruts, `0.05` par défaut |

## Équipe projet : architecte, lead dev, développeurs

Chaque projet a (Administration › Projets, modifiable par l'Admin, le Head of Office ou le PM) :

| Rôle projet | Fait quoi |
| --- | --- |
| Architecte | Dépose les documents du projet depuis « Mes projets » (sa page d'accueil) : TDR, comptes rendus de réunion, spécifications, architecture, code… en Word, Excel, PDF, archives (100 Mo max par fichier, plusieurs à la fois). L'équipe est prévenue par e-mail |
| Lead dev | Assigne les tâches aux développeurs du projet, voit toutes les tâches du projet, accorde ou refuse les estimations. Peut aussi déposer des documents |
| Développeurs | Consultent et téléchargent les documents ; estiment la durée des tâches qui leur sont assignées |

Sous chaque document, une **discussion** : les développeurs posent leurs questions, le lead dev et l'architecte
expliquent (messages mis en évidence), avant l'assignation des tâches. Les participants sont prévenus par e-mail.
Le bloc « Documents du projet » est dans « Mes projets » pour toute l'équipe projet.

Circuit d'une tâche assignée par le lead dev : **À estimer** → le développeur propose une durée → **Estimation à valider**
→ le lead dev l'accorde (en l'ajustant s'il veut) → la tâche est **active** : elle peut passer « En cours » et être déclarée dans la fiche de temps.
Un refus (avec motif) la renvoie au développeur. Chaque étape est tracée dans l'historique de la tâche et notifiée par e-mail.
Le PM du développeur peut aussi valider en l'absence du lead dev.

Les documents sont stockés sur disque dans `DOCUMENTS_DIR` (par défaut `data/documents`, dans le volume `backend-data` en Docker).
Ils sont visibles par l'équipe du projet, le Head of Office et l'Admin ; supprimables par leur auteur, l'architecte, le PM, le Head of Office ou l'Admin.

À la soumission, l'Excel est envoyé par e-mail et les saisies de la période sont verrouillées.
Un rapport renvoyé (« À corriger ») se déverrouille avec le commentaire du validateur.

Automatisations (heure de Dakar, modifiables dans `application.yml`) :

| Quand | Quoi |
| --- | --- |
| Jeudi 16h, vendredi 10h | Relance des consultants qui n'ont pas soumis leur rapport hebdo |
| Vendredi 18h | Chaque manager reçoit le ZIP des rapports de toute son équipe (sous-équipes comprises) + la liste des manquants |
| 18 et 19 du mois, 10h | Relance pour la fiche de temps mensuelle |
| Le 20, 8h | Le Head of Office reçoit le ZIP de toutes les fiches de la période écoulée + synthèse |

## Gestion des tâches et tableau de bord

- **Tâches** : priorité (Basse → Critique), début / échéance, charge estimée (jours), avancement (%).
  Trois vues : **Tableau** (Kanban, glisser-déposer pour changer le statut), **Liste** triable, **Planning** (Gantt, regroupé par consultant ou projet).
  Filtres rapides (en retard, bloquées, à rendre sous 7 jours, sans échéance), recherche, export CSV.
- **Fiche d'une tâche** (panneau latéral) : jours déclarés dans les fiches de temps vs charge estimée, commentaires
  (l'assigné et le créateur sont prévenus par e-mail) et historique automatique (création, statut, assignation, modifications).
- **Tableau de bord** (réservé aux managers, au Head of Office et à l'Admin ; ni les développeurs, ni les lead devs, ni les architectes) :
  le manager suit **ses développeurs et lead devs**, le Head of Office **les architectes**. Pour chacun : nombre de fiches soumises
  sur la période (hebdo + mensuelles), en attente, validées, à corriger, rapports hebdo en retard ou manquants, jours déclarés.
  Un clic sur une personne liste ses fiches (« Du lundi 5 au vendredi 9 octobre 2026 »), un clic sur une fiche ouvre son détail
  (validation / renvoi). Aussi : ponctualité des rapports hebdo et jours déclarés par projet.
- **Cloche de notification** (managers, Head of Office) : nombre de fiches reçues en attente de validation — rapports hebdo de
  l'équipe pour le manager, fiches mensuelles (et rapports hebdo des architectes) pour le Head of Office — avec leur liste.
  Le compteur baisse à la validation ou au renvoi. `GET /api/reports/inbox`.

Les graphiques sont dessinés en SVG, sans bibliothèque. Supervision : `GET /actuator/health`.

## Liaison GitLab (tâches ↔ commits)

Chaque tâche a une référence courte, **`TS-42`**, affichée dans « Tâches » avec le nom de branche à copier.
La synchronisation (toutes les 15 min, ou « Synchroniser maintenant » dans Administration › GitLab) relie :

| Lien | Comment |
| --- | --- |
| Commit → tâche | `TS-42` dans le **message du commit**, sinon dans le **titre / la branche de la MR**, sinon dans le **nom de branche** (`feature/TS-42-export-excel`) |
| Commit → consultant | e-mail de l'auteur du commit = e-mail du compte, ou une des « Identités Git » de l'utilisateur |
| Dépôt → projet | Administration › GitLab ; automatique si le groupe porte le nom du projet (`numflex/*` → NumFlex). Un projet peut avoir plusieurs dépôts |

Ce que ça apporte :
- **Ma semaine** : les commits du jour pas encore saisis sont proposés, un clic pour les ajouter à la fiche ;
- **Tâches** : nombre de commits par tâche, détail des commits et merge requests ;
- **Activité GitLab** (PM, Head of Office) : commits par consultant et projet, tâches « En cours » sans commit,
  commits sans tâche, jours avec commits mais sans ligne dans la fiche, auteurs GitLab non reconnus.

Les commits servent de preuve et d'aide à la saisie, pas de mesure du temps (analyse, réunions, architecture ne produisent pas de commits).

Configuration (variables d'environnement du backend) :

| Variable | Rôle |
| --- | --- |
| `GITLAB_TOKEN` | Jeton personnel ou de groupe avec le droit **`read_api`**. Sans jeton, la synchro est désactivée |
| `GITLAB_URL` | `https://it-gitlab.yas.sn` par défaut |
| `GITLAB_GROUPS` | Groupes suivis, ex. `numflex,NUMBER-ONE`. Vide : tous les dépôts dont le propriétaire du jeton est membre |
| `GITLAB_TASK_PREFIX` | `TS` par défaut |

Au premier passage, les 60 derniers jours sont importés.

## Démarrer en local

Prérequis : Java 21+, Maven, Node 20+.

```bash
# Backend (http://localhost:8083) — base H2 dans backend/data
cd backend && mvn spring-boot:run

# Frontend (http://localhost:4300)
cd frontend && npm install && npm start
```

Comptes de démonstration (mot de passe `Yas@2026`) :
`moussa@` (lead dev), `fatou@`, `cheikh@`, `aminata@`, `ibrahima@` (développeurs), `pape@`, `mariama@`, `ousmane@` (architectes),
`pm@` (Demba FALL, manager), `biteye@` (Mouhamadou Moustapha BITEYE, manager de Demba FALL),
`head@` (Toussaint BASSENE, Head of Office), `admin@` — tous en `@yas-itfactory.sn`.
Hiérarchie : les 5 développeurs → Demba FALL → Mouhamadou Moustapha BITEYE ; les 3 architectes → Toussaint BASSENE.
Sur NumFlex : Pape Samba NDOUR architecte, Moussa lead dev, Fatou, Cheikh et Ibrahima développeurs ;
sur CRM Entreprise : Mariama BA architecte, Aminata développeuse ; Interne : Ousmane GUEYE architecte.

Cas de test du tableau de bord et de la cloche (S-1 = semaine dernière ; vérifiés par `DashboardScenarioTest`) :

| Personne | Cas | Fiches |
| --- | --- | --- |
| Aminata DIALLO | Exemplaire | 11 rapports hebdo à l'heure (S-1 à valider) + fiche mensuelle validée |
| Ibrahima NDIAYE | Retards et renvois | S-1 à corriger, S-2 en retard, S-3 manquant ; fiche mensuelle à valider par le Head of Office |
| Fatou SOW | À valider | S-1 soumis, en attente |
| Cheikh FALL | Soumis en retard | S-1 soumis le lundi, en attente |
| Moussa THIOUNE | Oubli | S-1 manquant |
| Pape Samba NDOUR | À valider | S-1 et fiche mensuelle en attente du Head of Office |
| Mariama BA | Retard | S-2 en retard, S-3 manquant |
| Ousmane GUEYE | Renvoi et oubli | S-1 manquant, S-2 à corriger |

Avec `pm@`, la cloche affiche les rapports hebdo d'Aminata, Fatou et Cheikh ; avec `head@`, celui de Pape Samba
et les fiches mensuelles de Pape Samba et d'Ibrahima. Ces données s'ajoutent aussi à une base existante au prochain démarrage.

En local, `MAIL_ENABLED=false` : les e-mails ne partent pas, ils sont déposés avec leurs pièces jointes
dans `backend/data/outbox/` (`message.txt` : destinataires et objet, `message.html` : contenu) pour vérification.

## Production (Docker)

```bash
cp .env.example .env   # renseigner JWT_SECRET, DB_PASSWORD, SMTP…
docker compose up -d --build
# → http://<serveur>:8081
```

Passer `APP_SEED=false` pour ne pas créer les comptes de démonstration, puis créer l'admin réel.
Pour Office 365 : `MAIL_ENABLED=true`, `MAIL_HOST=smtp.office365.com`, `MAIL_PORT=587`, `MAIL_USER`/`MAIL_PASSWORD` d'une boîte d'envoi.

## Technique

- **Backend** : Spring Boot 3.5 (Java 21), Spring Security + JWT, Spring Data JPA, Apache POI, PDFBox (factures), Spring Mail, `@Scheduled`.
  PostgreSQL en production, H2 en développement.
- **Frontend** : Angular 20 (standalone, signals), sans dépendance UI externe, responsive.
- Tests : `cd backend && mvn test` (périodes 20 → 19, flux soumission / validation, format Excel, droits d'accès).

### API principale

| Méthode | Route | Rôle |
| --- | --- | --- |
| POST | `/api/auth/login` | Connexion |
| GET/POST/PUT/DELETE | `/api/entries` | Saisies journalières |
| GET/POST/PUT/PATCH/DELETE | `/api/tasks` | Tâches et assignation |
| GET | `/api/reports/timesheet?type=HEBDO\|MENSUEL&date=` | Fiche de la période |
| POST | `/api/reports/submit`, `/withdraw` | Soumettre / retirer |
| POST | `/api/reports/{id}/approve`, `/reject` | Valider / renvoyer |
| GET | `/api/reports/team?type=&date=` | Suivi de l'équipe |
| GET | `/api/reports/excel`, `/api/reports/team/zip` | Téléchargements |
| GET/POST/PUT | `/api/users`, `/api/projects` | Référentiel (projets : architecte, lead dev, développeurs) |
| GET | `/api/projects/spaces` | Mes projets, mes rôles et droits |
| GET/POST | `/api/projects/{id}/documents` | Documents du projet / dépôt (multipart `files`, `description`) |
| GET/DELETE | `/api/projects/{id}/documents/{docId}` | Télécharger / supprimer |
| POST | `/api/tasks/{id}/estimate`, `/estimate/approve`, `/estimate/reject` | Proposer / accorder / refuser une estimation |
| GET | `/api/tasks/{id}/git` | Commits et MR liés à une tâche |
| GET | `/api/tasks/{id}/activity` | Historique et commentaires d'une tâche |
| POST/DELETE | `/api/tasks/{id}/comments`, `/comments/{commentId}` | Commenter / supprimer son commentaire |
| GET | `/api/dashboard?projectId=&weeks=` | Tableau de bord (managers, Head of Office, Admin) |
| GET | `/api/reports/inbox` | Fiches reçues en attente de validation (cloche) |
| GET/PUT, POST | `/api/invoices/month/{AAAA-MM}`, `/api/invoices`, `/api/invoices/{id}/submit` | Facture du mois : proposition, enregistrement, envoi |
| GET | `/api/invoices/{id}/pdf`, `/api/invoices/team?month=` | PDF, suivi Head of Office |
| POST | `/api/invoices/{id}/approve`, `/reject` | Valider / renvoyer une facture |
| GET/PUT/DELETE | `/api/invoices/profile`, `/api/invoices/profile/signature` | Coordonnées et signature |
| GET | `/api/git/suggestions?from=&to=` | Commits pas encore saisis dans la fiche |
| GET | `/api/git/activity?type=&date=` | Vue globale GitLab de l'équipe |
| GET/PUT, POST | `/api/git/repos`, `/api/git/sync` | Dépôts ↔ projets, synchronisation |

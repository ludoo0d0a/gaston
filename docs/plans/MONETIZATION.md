# Plan de monétisation Gaston

Statut : proposition (oct. 2026). Les prix concurrents viennent de sources anciennes (Coyote 2021) : à revérifier sur le Play Store.

## Positionnement

- **Coyote** : radar/trafic en abonnement (7,99 €/mois, ~65-87 €/an en 2021). Cher, mono-usage.
- **Waze** : gratuit, pub, pas de prix carburant fiables ni IRVE.
- **Essence&Co** : gratuit + abonnement ; « stations sur itinéraire » est premium.
- **Fuelio, PlugShare, SpotHero, MyRadar** : un seul métier chacun.
- **Gaston** : assistant auto unique (essence, élec, radar, parking, entretien, coûts). Message : « Tout Coyote pour ~20 €/an, plus essence, élec et entretien. »

## Modèle

Freemium avec abonnement comme revenu principal + achat à vie. Pub limitée au téléphone (bannière dashboard, jamais en navigation). **Aucune pub sur Android Auto** (règles Google, sécurité). Voir `docs/ADS_SETUP.md`.

Le gratuit doit rester assez bon pour faire installer l'app (notes Play Store, découverte).

## Gratuit / Premium

| Fonction | Gratuit | Premium |
|---|---|---|
| Prix carburant autour de moi | Oui | Oui |
| IRVE autour de moi | Oui | + disponibilité temps réel |
| Android Auto | Limité : liste de 3-5 stations, pas de carte | Carte complète, tous les POI, tri perso |
| Recherche sur itinéraire | 1 / jour | Illimitée |
| Favoris | 3 | Illimités + alertes prix |
| Historique / prévision prix | 7 jours | Complet (Brent, 3 jours, par station) |
| Alertes baisse de prix | Non | Oui (levier n°1) |
| Radar | Zones de danger | Alertes complètes (cadre légal ci-dessous) |
| Entretien / coûts | 1 véhicule, saisie manuelle | Multi-véhicules, rappels, export |
| Stationnement | Recherche | Alertes, historique |
| Cartes carburant / flotte | Non | Oui |
| Pub | Bannière téléphone | Aucune |

Principe : le gratuit résout le besoin immédiat ; le premium débloque l'anticipation (alertes, prévisions), la répétition (illimité) et la personnalisation. Android Auto est le meilleur levier.

## Prix (à tester)

- Mensuel 2,99 € (test 1,99 €)
- Annuel 19,99 € (offre mise en avant, essai gratuit 7 jours)
- À vie 39,99-49,99 €
- Les coûts d'API (TIPI Action B/C, données prix) doivent être couverts. Vérifier TVA / statut fiscal côté Google Play.

## Point légal radar

En France, seules les « zones de danger » peuvent être signalées, pas la position exacte. S'appuyer sur `docs/RADARS_NF469.md`. La fiche Play Store doit être formulée en conséquence.

## Technique : RevenueCat + module Entitlements

**RevenueCat recommandé** (validation serveur, états d'abonnement, paywalls, A/B prix, analytics, SDK KMP), gratuit à faible revenu puis ~1 %. Alternative : Play Billing seul (zéro dépendance mais validation serveur à construire).

Le billing est derrière une interface pour pouvoir changer de fournisseur :

```
UI / Android Auto / ViewModels
        │ lit
   Entitlements (interface, :shared)
   ├─ plan: StateFlow<Plan>      Free | Premium
   ├─ can(feature): Boolean
   └─ limit(feature): Int
        ▲ alimenté par
   BillingProvider (:androidApp) -> RevenueCat | Play Billing
      purchase(...), restore()
```

- `Feature` : liste fermée (FavoritesUnlimited, RouteSearchUnlimited, PriceAlerts, AutoFullMap, FullForecast, MultiVehicle…).
- Toutes les règles gratuit/premium dans un seul fichier, testable en Kotlin pur.
- Dernier plan connu en cache (DataStore) avec expiration : fonctionne hors ligne en voiture.
- Jamais de blocage de l'app si le SDK échoue : dernier état connu ou gratuit.
- `paywall.show(trigger = Feature.X)` contextuel (jamais au lancement) pour mesurer quelle limite convertit.
- `FakeEntitlements` + flag debug pour tester Free/Premium.
- Prix et offres gérés dans RevenueCat, pas dans le code.

## Feuille de route

1. **M0-1** : module Entitlements, RevenueCat, paywall contextuel, restauration des achats, flags de fonctions.
2. **M1-2** : analytics (limite atteinte, paywall vu, achat) avant toute optimisation.
3. **M2-3** : lancement avec essai 7 jours, offre de lancement -40 % la 1re année.
4. **M3-6** : ASO (« essence pas cher », « borne de recharge », « Android Auto carburant »), SEO via page prix du jour / tendance Brent, communautés (r/AndroidAuto, r/france, forums VE), partenariats stations/réseaux de recharge.
5. **M6+** : A/B prix et placement du paywall, offre famille / multi-véhicules.

## Revenus complémentaires

- Affiliation : cartes carburant (Routex, DKV, UTA), assurance auto, comparateurs d'entretien.
- B2B flotte (plus tard) : 5-10 €/véhicule/mois.

## Priorités

1. Mesurer d'abord ; viser 2-5 % de conversion des actifs vers le premium.
2. Alertes baisse de prix = meilleure justification d'abonnement, peu coûteuse.
3. Paywall avant d'investir en acquisition.

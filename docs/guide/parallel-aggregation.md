# Parallel API Aggregation

The classic BFF (Backend for Frontend) pattern: aggregate data from multiple microservices into a single response.

## The scenario

A dashboard endpoint that fetches user profile, cart, promotions, and loyalty tier — then uses those results to fetch recommendations and promotions tailored to the user.

**14 service calls, 5 phases, 3 dependency points.**

## With raw coroutines

```kotlin
val dashboard = coroutineScope {
    val dProfile = async { fetchProfile(userId) }
    val dPrefs = async { fetchPreferences(userId) }
    val dTier = async { fetchLoyaltyTier(userId) }
    val profile = dProfile.await()
    val prefs = dPrefs.await()
    val tier = dTier.await()

    val ctx = UserContext(profile, prefs, tier)

    val dRecs = async { fetchRecommendations(ctx.profile) }
    val dPromos = async { fetchPromotions(ctx.tier) }
    val dTrending = async { fetchTrending(ctx.prefs) }
    val dHistory = async { fetchHistory(ctx.profile) }
    // ... more shuttle variables, more phases
}
```

Problems: invisible phases, shuttle variables, easy to accidentally serialize calls.

## With KAP

```kotlin
val dashboard: FinalDashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐
        .with { fetchPreferences(userId) }   // ├─ phase 1: parallel
        .with { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::EnrichedContent)
                .with { fetchRecommendations(ctx.profile) }  // ┐
                .with { fetchPromotions(ctx.tier) }           // ├─ phase 2: parallel
                .with { fetchTrending(ctx.prefs) }            // │
                .with { fetchHistory(ctx.profile) }           // ┘
                .andThen { enriched ->                         // ── barrier
                    kap(::FinalDashboard)
                        .with { renderLayout(ctx, enriched) }     // ┐ phase 3
                        .with { trackAnalytics(ctx, enriched) }   // ┘
                }
        }
}
```

```
t=0ms   ─── fetchProfile ──────┐
t=0ms   ─── fetchPreferences ──├─ phase 1 (parallel, all 3)
t=0ms   ─── fetchLoyaltyTier ──┘
t=50ms  ─── andThen { ctx -> }  ── barrier
t=50ms  ─── fetchRecommendations ──┐
t=50ms  ─── fetchPromotions ───────├─ phase 2 (parallel, all 4)
t=50ms  ─── fetchTrending ─────────┤
t=50ms  ─── fetchHistory ──────────┘
t=90ms  ─── andThen { enriched -> } ── barrier
t=90ms  ─── renderLayout ──┐
t=90ms  ─── trackAnalytics ┘─ phase 3 (parallel)
t=115ms ─── FinalDashboard ready
```

**115ms vs 460ms sequential** — 4x speedup, and the dependency graph is visible in the code.

## Adding resilience

In production, services fail. Add retry and timeout:

```kotlin
val dashboard: FinalDashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }
        .with { fetchPreferences(userId) }
        .with { fetchLoyaltyTier(userId) }
        .timeout(2.seconds) { defaultContext() }  // phase 1 timeout with fallback
        .andThen { ctx ->
            kap(::EnrichedContent)
                .with {
                    Kap { fetchRecommendations(ctx.profile) }
                        .retry(Schedule.times<Throwable>(3) and Schedule.exponential(50.milliseconds))
                        .recover { emptyRecommendations() }
                }
                .with { fetchPromotions(ctx.tier) }
                .with { fetchTrending(ctx.prefs) }
                .with { fetchHistory(ctx.profile) }
        }
}
```

## Partial failure with `.settled()`

What if one source fails but you still want the rest?

```kotlin
val dashboard = Async {
    kap { user: Result<String>, cart: String, config: String ->
        PartialDashboard(user.getOrDefault("anonymous"), cart, config)
    }
        .with(Kap { fetchUser() }.settled())   // wrapped in Result
        .with { fetchCart() }
        .with { fetchConfig() }
}
// fetchUser fails? Dashboard still builds with "anonymous".
```

## Bounded traversal

200 product IDs, downstream handles 10 concurrent requests:

```kotlin
val products = Async {
    productIds.traverse(concurrency = 10) { id ->
        Kap { fetchProduct(id) }
    }
}
```

## Try it

```bash
./gradlew :examples:dashboard-aggregator:run
./gradlew :examples:ktor-integration:run
```

The [ktor-integration](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/ktor-integration) example is a full Ktor HTTP server with 28 integration tests.

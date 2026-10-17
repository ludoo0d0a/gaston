package fr.geoking.gaston.entitlements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeEntitlements(
    initialPlan: Plan = Plan.FREE
) : Entitlements {

    private val _plan = MutableStateFlow(initialPlan)
    override val plan: StateFlow<Plan> = _plan.asStateFlow()

    private val delegate = DefaultEntitlements(initialPlan)

    fun setPlan(plan: Plan) {
        _plan.value = plan
        delegate.setPlan(plan)
    }

    override fun can(feature: Feature): Boolean {
        delegate.setPlan(_plan.value)
        return delegate.can(feature)
    }

    override fun limit(feature: Feature): Int {
        delegate.setPlan(_plan.value)
        return delegate.limit(feature)
    }
}

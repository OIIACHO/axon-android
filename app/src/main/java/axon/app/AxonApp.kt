package axon.app

import android.app.Application
import axon.app.core.SubscriptionStore

class AxonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SubscriptionStore.init(this)
    }
}

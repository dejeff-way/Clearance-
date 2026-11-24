package com.forma.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.forma.app.di.AppContainer
import com.forma.app.health.HcAvailability
import com.forma.app.health.SyncWorker
import kotlinx.coroutines.launch

open class FormaApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Overridden in tests to provide an in-memory database and fake Health Connect. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        container.appScope.launch { container.initialize() }

        // Foreground sync: whenever the app comes to the foreground, refresh Health Connect (if permitted).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.appScope.launch {
                    runCatching {
                        if (container.healthSource.availability() == HcAvailability.AVAILABLE &&
                            container.healthSource.grantedTypes().isNotEmpty()
                        ) container.healthSyncer.sync()
                        val meta = container.db.syncMeta().get()
                        val bg = meta?.backgroundSyncEnabled == true && container.healthSource.backgroundReadGranted()
                        SyncWorker.schedule(this@FormaApp, bg)
                    }
                }
            }
        })
    }
}

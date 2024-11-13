package com.icapps.background_location_tracker

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.icapps.background_location_tracker.flutter.FlutterBackgroundManager
import com.icapps.background_location_tracker.flutter.FlutterLifecycleAdapter
import com.icapps.background_location_tracker.utils.ActivityCounter
import com.icapps.background_location_tracker.utils.Logger
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class BackgroundLocationTrackerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private var lifecycle: Lifecycle? = null
    private var methodCallHelper: MethodCallHelper? = null

    // This method is called when the plugin is attached to the Flutter engine
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Register the background location manager
        registerBackgroundLocationManager(binding.binaryMessenger, binding.applicationContext)
    }

    // This method is called when the plugin is detached from the Flutter engine
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // You can clean up resources here if needed
    }

    // This method handles the method calls from Flutter
    override fun onMethodCall(call: MethodCall, result: Result) {
        methodCallHelper?.handle(call, result)
    }

    // Called when the plugin is attached to an activity
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding)
        if (methodCallHelper == null) {
            ActivityCounter.attach(binding.activity)
            methodCallHelper = MethodCallHelper.getInstance(binding.activity.applicationContext)
        }
        // Ensure the method call helper observes lifecycle events
        methodCallHelper?.let {
            lifecycle?.removeObserver(it)
            lifecycle?.addObserver(it)
        }
    }

    // Called when the plugin is detached from the activity
    override fun onDetachedFromActivity() {
        // Handle cleanup if necessary
    }

    // Called when the activity is reattached for configuration changes
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

    // Called when the activity is detached for configuration changes
    override fun onDetachedFromActivityForConfigChanges() {}

    companion object {
        private const val TAG = "FBLTPlugin"
        private const val FOREGROUND_CHANNEL_NAME = "com.icapps.background_location_tracker/foreground_channel"

        var pluginRegistryCallback: PluginRegistry.PluginRegistrantCallback? = null

        // This method registers the background location manager
        @JvmStatic
        private fun registerBackgroundLocationManager(messenger: BinaryMessenger, ctx: Context) {
            val channel = MethodChannel(messenger, FOREGROUND_CHANNEL_NAME)
            val plugin = BackgroundLocationTrackerPlugin()
            plugin.methodCallHelper = MethodCallHelper.getInstance(ctx)
            channel.setMethodCallHandler(plugin)
        }

        // Method to register the plugin with the older Flutter embedding (Deprecated)
        @Deprecated(message = "Use the Android v2 embedding method.")
        @JvmStatic
        fun registerWith(registrar: PluginRegistry.Registrar) {
            val activity = registrar.activity()
            if (activity == null) {
                Logger.debug(TAG, "Activity should not be null while registering this plugin")
                return
            }

            val lifecycle: Lifecycle = if (activity is LifecycleOwner) {
                activity.lifecycle
            } else {
                Logger.debug(TAG, "Your activity has not implemented a lifecycle owner. We will create one for you.")
                ProxyLifecycleProvider(activity).lifecycle
            }

            ActivityCounter.attach(activity)
            val channel = MethodChannel(registrar.messenger(), FOREGROUND_CHANNEL_NAME)
            val plugin = BackgroundLocationTrackerPlugin()
            plugin.methodCallHelper = MethodCallHelper.getInstance(registrar.activeContext())
            channel.setMethodCallHandler(plugin)
        }

        // Deprecated callback method
        @Deprecated(message = "Use the Android v2 embedding method.")
        @JvmStatic
        fun setPluginRegistrantCallback(pluginRegistryCallback: PluginRegistry.PluginRegistrantCallback) {
            BackgroundLocationTrackerPlugin.pluginRegistryCallback = pluginRegistryCallback
        }
    }

    // Proxy lifecycle provider for older embedding support
    @Deprecated(message = "Use the Android v2 embedding method.")
    private class ProxyLifecycleProvider internal constructor(activity: Activity) : Application.ActivityLifecycleCallbacks, LifecycleOwner {
        val lifecycle = LifecycleRegistry(this) // Public access enabled
        private val registrarActivityHashCode: Int = activity.hashCode()

        init {
            activity.application.registerActivityLifecycleCallbacks(this)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity.hashCode() != registrarActivityHashCode) {
                return
            }
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        override fun onActivityStarted(activity: Activity) {
            if (activity.hashCode() != registrarActivityHashCode) {
                return
            }
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity.hashCode() != registrarActivityHashCode) {
                return
            }
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity.hashCode() != registrarActivityHashCode) {
                return
            }
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity.hashCode() != registrarActivityHashCode) {
                return
            }
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            if (activity.hashCode() != registrarActivityHashCode) {
                return
            }
            activity.application.unregisterActivityLifecycleCallbacks(this)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }

        override fun getLifecycle(): Lifecycle = lifecycle
    }
}

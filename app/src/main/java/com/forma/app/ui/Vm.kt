package com.forma.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forma.app.di.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

/** Creates a ViewModel from the app container, scoped to the current navigation entry. */
@Composable
inline fun <reified VM : ViewModel> formaViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

/** Navigation must happen on the main thread, even after a suspending database write. */
suspend fun androidx.navigation.NavController.popOnMain() {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { popBackStack() }
}

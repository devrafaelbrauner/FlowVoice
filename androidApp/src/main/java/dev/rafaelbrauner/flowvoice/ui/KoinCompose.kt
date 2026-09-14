package dev.rafaelbrauner.flowvoice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.java.KoinJavaComponent

@Composable
inline fun <reified T : Any> rememberKoin(): T = remember { KoinJavaComponent.get(T::class.java) }

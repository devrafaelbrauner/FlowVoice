package dev.rafaelbrauner.flowvoice.shared

fun greeting(name: String = APP_NAME): String = "Hello, $name — ${platformName()}"
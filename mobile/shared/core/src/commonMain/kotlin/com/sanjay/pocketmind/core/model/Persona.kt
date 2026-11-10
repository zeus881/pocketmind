package com.sanjay.pocketmind.core.model

/**
 * A way of answering: instructions for the model plus, optionally, its own creativity
 * (temperature). Built-in personas ship with the app; users can create their own.
 */
data class Persona(
    val id: String,
    val name: String,
    val instructions: String,
    /** Null = use the temperature from Settings. */
    val temperature: Float? = null,
    val builtIn: Boolean = false,
)

/** A saved prompt the user can insert into the chat input ("prompt library"). */
data class SavedPrompt(val id: String, val title: String, val text: String)

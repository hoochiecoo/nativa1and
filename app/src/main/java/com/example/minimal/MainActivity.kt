package com.example.minimal

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "Hello CI/CD World!"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        setContentView(textView)
    }
}
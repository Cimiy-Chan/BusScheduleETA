package org.cimiy.busscheduleeta

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes

object AppToast {
    fun show(context: Context, message: String, @DrawableRes icon: Int = R.drawable.ic_toast_ok) {
        val view = LayoutInflater.from(context.applicationContext)
            .inflate(R.layout.view_app_toast, null)
        view.findViewById<ImageView>(R.id.ivIcon).setImageResource(icon)
        view.findViewById<TextView>(R.id.tvMessage).text = message
        Toast(context.applicationContext).apply {
            duration = Toast.LENGTH_SHORT
            this.view = view
            show()
        }
    }
}
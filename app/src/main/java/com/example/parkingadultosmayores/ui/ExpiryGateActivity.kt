
package com.example.parkingadultosmayores.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.parkingadultosmayores.licensing.ExpirationGate
import com.example.parkingadultosmayores.ui.main.MainActivity

class ExpiryGateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ExpirationGate.isExpired()) {
            ExpirationGate.showExpiredAndClose(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

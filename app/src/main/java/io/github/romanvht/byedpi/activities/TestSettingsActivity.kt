package io.github.romanvht.byedpi.activities

import android.os.Bundle
import android.view.MenuItem
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.fragments.DomainListsFragment
import io.github.romanvht.byedpi.fragments.ProxyTestSettingsFragment

class TestSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_settings)

        val openFragment = intent.getStringExtra("open_fragment")

        when (openFragment) {
            "domain_lists" -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.test_settings, DomainListsFragment())
                    .commit()
            }
            else -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.test_settings, ProxyTestSettingsFragment())
                    .commit()
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
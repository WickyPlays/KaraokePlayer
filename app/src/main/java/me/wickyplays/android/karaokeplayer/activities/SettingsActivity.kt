package me.wickyplays.android.karaokeplayer.activities

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.fragements.SettingsGeneralFragment
import me.wickyplays.android.karaokeplayer.fragements.SettingsLyricsFragment

class SettingsActivity : AppCompatActivity() {

    private val colorPresets = listOf(
        "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF",
        "#00FFFF", "#FFFFFF", "#000000", "#FFA500", "#800080",
        "#A52A2A", "#008000", "#808080", "#FFC0CB", "#40E0D0",
        "#E6E6FA", "#F5F5DC", "#FF6347", "#7FFFD4", "#D2691E"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val tabLayout: TabLayout = findViewById(R.id.tabLayout)
        val backButton: ImageButton = findViewById(R.id.backButton)

        viewPager.adapter = SettingsPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.settings_general)
                1 -> getString(R.string.settings_lyrics)
                else -> ""
            }
        }.attach()

        backButton.setOnClickListener {
            finish()
        }
    }

    inner class SettingsPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int) = when (position) {
            0 -> SettingsGeneralFragment()
            1 -> SettingsLyricsFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList("colorPresets", ArrayList(colorPresets))
                }
            }
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
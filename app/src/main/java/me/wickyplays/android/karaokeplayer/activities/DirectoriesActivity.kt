package me.wickyplays.android.karaokeplayer.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.cores.directories.KaraokeDirectoriesCore
import me.wickyplays.android.karaokeplayer.databinding.ActivityDirectoryBinding
import me.wickyplays.android.karaokeplayer.fragements.DirectoriesFragment
import me.wickyplays.android.karaokeplayer.fragements.DirectoriesOnlineFragment
import me.wickyplays.android.karaokeplayer.fragements.DirectoriesUploadFragment

class DirectoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDirectoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_directory)

        binding = ActivityDirectoryBinding.inflate(layoutInflater);

        KaraokeDirectoriesCore.getInstance().initialize(this);

        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val tabLayout: TabLayout = findViewById(R.id.tabLayout)

        viewPager.adapter = TabsAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Directory"
                1 -> tab.text = "Online"
                2 -> tab.text = "Upload"
            }
        }.attach()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
            }
        }
    }
}

class TabsAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DirectoriesFragment()
            1 -> DirectoriesOnlineFragment()
            2 -> DirectoriesUploadFragment()
            else -> DirectoriesFragment()
        }
    }
}
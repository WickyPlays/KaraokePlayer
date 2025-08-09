package me.wickyplays.android.karaokeplayer.fragements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.adapters.SettingLyricColorAdapter

class SettingsLyricsFragment : Fragment() {

    private lateinit var outlinedColorRecycler: RecyclerView
    private lateinit var filledColorRecycler: RecyclerView
    private lateinit var colorPresets: List<String>
    private var outlinedColorSelected = -1
    private var filledColorSelected = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_lyrics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        colorPresets = arguments?.getStringArrayList("colorPresets") ?: emptyList()

        outlinedColorRecycler = view.findViewById(R.id.outlinedColorRecycler)
        filledColorRecycler = view.findViewById(R.id.filledColorRecycler)

        val layoutManager = GridLayoutManager(context, 5)
        outlinedColorRecycler.layoutManager = layoutManager
        filledColorRecycler.layoutManager = GridLayoutManager(context, 5)

        val savedOutlinedColor = "#FFFFFF"
        val savedFilledColor = "#000000"

        outlinedColorSelected = colorPresets.indexOf(savedOutlinedColor)
        filledColorSelected = colorPresets.indexOf(savedFilledColor)

        outlinedColorRecycler.adapter =
            SettingLyricColorAdapter(colorPresets, outlinedColorSelected) { position ->
                outlinedColorSelected = position
            }

        filledColorRecycler.adapter = SettingLyricColorAdapter(colorPresets, filledColorSelected) { position ->
            filledColorSelected = position
        }
    }
}
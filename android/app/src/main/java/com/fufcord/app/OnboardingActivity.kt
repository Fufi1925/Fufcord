/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.fufcord.app.databinding.ActivityOnboardingBinding
import com.fufcord.app.databinding.ItemOnboardingPageBinding

/** Onboarding beim allerersten Start: 4 Seiten mit Tiefen-Übergang. */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private lateinit var prefs: PrefsManager

    private data class Page(val title: String, val text: String, val icon: Int, val orb: Int)

    private val pages: List<Page> by lazy {
        listOf(
            Page(getString(R.string.on1_title), getString(R.string.on1_text),
                R.drawable.ic_sparkles, R.drawable.orb_violet),
            Page(getString(R.string.on2_title), getString(R.string.on2_text),
                R.drawable.ic_shield, R.drawable.orb_cyan),
            Page(getString(R.string.on3_title), getString(R.string.on3_text),
                R.drawable.ic_image, R.drawable.orb_pink),
            Page(getString(R.string.on4_title), getString(R.string.on4_text),
                R.drawable.ic_play, R.drawable.orb_cyan)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        if (prefs.onboardingDone) {
            routeNext()
            return
        }
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.pager.adapter = PageAdapter(pages)
        b.pager.setPageTransformer(DepthTransformer())
        buildDots(0)

        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                buildDots(position)
                val last = position == pages.size - 1
                b.btnNext.text = getString(if (last) R.string.on_start else R.string.on_next)
                b.btnNext.setIconResource(if (last) R.drawable.ic_check else R.drawable.ic_chevron_right)
                b.btnSkip.animate().alpha(if (last) 0f else 1f).setDuration(200).start()
                b.btnSkip.isEnabled = !last
            }
        })

        b.btnSkip.setOnClickListener { finishOnboarding() }
        b.btnNext.setOnClickListener {
            val cur = b.pager.currentItem
            if (cur == pages.size - 1) finishOnboarding()
            else b.pager.setCurrentItem(cur + 1, true)
        }
    }

    private fun buildDots(active: Int) {
        b.dots.removeAllViews()
        for (i in pages.indices) {
            val d = ImageView(this)
            d.setImageResource(if (i == active) R.drawable.dot_page_active else R.drawable.dot_page_idle)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            d.layoutParams = lp
            b.dots.addView(d)
        }
    }

    private fun finishOnboarding() {
        prefs.onboardingDone = true
        routeNext()
    }

    private fun routeNext() {
        val next = if (prefs.setupDone) MainActivity::class.java else SetupActivity::class.java
        startActivity(Intent(this, next))
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private class PageAdapter(val items: List<Page>) : RecyclerView.Adapter<PageAdapter.Holder>() {
        class Holder(val vb: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(vb.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val vb = ItemOnboardingPageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
            return Holder(vb)
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            val p = items[position]
            h.vb.pageTitle.text = p.title
            h.vb.pageText.text = p.text
            h.vb.pageIcon.setImageResource(p.icon)
            h.vb.pageOrb.setImageResource(p.orb)
        }

        override fun getItemCount() = items.size
    }

    /** Seiten zoomen/blenden sich tiefenmäßig weg. */
    private class DepthTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: android.view.View, position: Float) {
            when {
                position < -1 || position > 1 -> page.alpha = 0f
                position <= 0 -> {
                    page.alpha = 1f
                    page.translationX = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                }
                else -> {
                    page.alpha = 1f - position
                    page.translationX = -position * page.width * 0.25f
                    val s = 1f - position * 0.12f
                    page.scaleX = s
                    page.scaleY = s
                }
            }
        }
    }
}

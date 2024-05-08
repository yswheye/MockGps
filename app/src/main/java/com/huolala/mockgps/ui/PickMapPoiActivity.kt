package com.huolala.mockgps.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.adaper.PoiListAdapter
import com.huolala.mockgps.adaper.SimpleDividerDecoration
import com.huolala.mockgps.databinding.ActivityPickBinding
import com.huolala.mockgps.manager.FollowMode
import com.huolala.mockgps.manager.MapLocationManager
import com.huolala.mockgps.model.PoiInfoModel
import com.huolala.mockgps.model.PoiInfoType
import kotlinx.android.synthetic.main.activity_pick.confirm_location
import kotlinx.android.synthetic.main.activity_pick.et_search
import kotlinx.android.synthetic.main.activity_pick.et_search_city
import kotlinx.android.synthetic.main.activity_pick.iv_cur_location
import kotlinx.android.synthetic.main.activity_pick.iv_search
import kotlinx.android.synthetic.main.activity_pick.ll_search
import kotlinx.android.synthetic.main.activity_pick.mapview
import kotlinx.android.synthetic.main.activity_pick.recycler
import kotlinx.android.synthetic.main.activity_pick.tv_lonlat
import kotlinx.android.synthetic.main.activity_pick.tv_poi_name
import java.lang.ref.WeakReference


/**
 * @author jiayu.liu
 */
class PickMapPoiActivity : BaseActivity<ActivityPickBinding, BaseViewModel>(),
    View.OnClickListener {
    private val REVERSE_GEO_CODE = 0
    private val DEFAULT_DELAYED: Long = 300
    private lateinit var mBaiduMap: BaiduMap
    private lateinit var mCoder: GeoCoder
    private var poiListAdapter: PoiListAdapter = PoiListAdapter()
    private var mPoiInfoModel: PoiInfoModel? = null
    private var mSuggestionSearch: SuggestionSearch = SuggestionSearch.newInstance()
    private var mHandler: PickMapPoiHandler? = null
    private var mapLocationManager: MapLocationManager? = null

    @PoiInfoType
    private var poiInfoType: Int = PoiInfoType.DEFAULT

    //检索
    private val listener: OnGetSuggestionResultListener =
        OnGetSuggestionResultListener { suggestionResult -> //处理sug检索结果
            if (et_search.visibility == View.VISIBLE && !TextUtils.isEmpty(et_search.text)) {
                suggestionResult.allSuggestions?.let {
                    poiListAdapter.setData(it)
                    recycler.visibility = View.VISIBLE
                }
            }
        }

    private fun reverseGeoCode(latLng: LatLng?) {
        latLng?.let {
            mCoder.reverseGeoCode(
                ReverseGeoCodeOption()
                    .location(it)
                    .newVersion(1)
                    .radius(500)
            )
        }
    }

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_pick
    }

    override fun initView() {
        mHandler = PickMapPoiHandler(this)
        poiInfoType =
            intent?.run { getIntExtra("from_tag", PoiInfoType.DEFAULT) } ?: PoiInfoType.DEFAULT
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = poiListAdapter
        recycler.addItemDecoration(SimpleDividerDecoration(this))
        recycler.itemAnimator = null

        poiListAdapter.setOnItemClickListener(object : PoiListAdapter.OnItemClickListener {
            override fun onItemClick(poiInfo: SuggestionResult.SuggestionInfo) {
                poiInfo.run {
                    if (pt == null) {
                        return@run
                    }
                    this@PickMapPoiActivity.mPoiInfoModel = PoiInfoModel(
                        pt,
                        poiInfo.uid,
                        key,
                        poiInfoType
                    )
                    tv_poi_name.text = key
                    tv_lonlat.text = pt?.toString()
                    editViewShow(false)
                    changeCenterLatLng(pt.latitude, pt.longitude)
                }
            }
        })

        ClickUtils.applySingleDebouncing(iv_search, this)
        ClickUtils.applySingleDebouncing(confirm_location, this)
        ClickUtils.applySingleDebouncing(iv_cur_location, this)

        et_search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!TextUtils.isEmpty(s)) {
                    mSuggestionSearch.requestSuggestion(
                        SuggestionSearchOption()
                            .city(if (et_search_city.text?.isNotEmpty() == true) et_search_city.text.toString() else "中国")
                            .keyword(s.toString()) //必填
                    )
                } else {
                    poiListAdapter.setData(null)
                    recycler.visibility = View.GONE
                }

            }

        })
        initMap()
    }

    override fun initData() {
    }

    override fun initObserver() {
    }

    private fun initMap() {
        mBaiduMap = mapview.map

        mBaiduMap.uiSettings?.run {
            isRotateGesturesEnabled = false
            isOverlookingGesturesEnabled = false
        }

        mCoder = GeoCoder.newInstance()
        mCoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult?) {

            }

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult?) {
                reverseGeoCodeResult?.run {
                    if (error != SearchResult.ERRORNO.NO_ERROR) {
                        Toast.makeText(
                            this@PickMapPoiActivity,
                            "逆地理编码失败",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                        return
                    }

                    //详细地址
                    reverseGeoCodeResult.poiList?.run {
                        if (!isEmpty()) {
                            val poiInfo = get(0)
                            mPoiInfoModel = PoiInfoModel(
                                location,
                                poiInfo?.uid,
                                poiInfo?.name,
                                poiInfoType
                            )
                            tv_poi_name.text = poiInfo?.name
                            tv_lonlat.text = poiInfo?.location?.toString()
                        }
                    }
                }
            }
        })

        var follow = FollowMode.MODE_SINGLE

        intent.getParcelableExtra<PoiInfoModel>("model")?.run model@{
            mPoiInfoModel = this
            latLng?.run {
                mPoiInfoModel = this@model
                tv_poi_name.text = this@model.name
                tv_lonlat.text = this@model.latLng.toString()
                editViewShow(false)
                changeCenterLatLng(latitude, longitude)
                follow = FollowMode.MODE_NONE
            }
        }

        //设置locationClientOption
        mapLocationManager = MapLocationManager(this, mBaiduMap, follow)
        mSuggestionSearch.setOnGetSuggestionResultListener(listener)

        mBaiduMap.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: MapStatus?) {
            }

            override fun onMapStatusChangeStart(mapStatus: MapStatus?, p1: Int) {
            }

            override fun onMapStatusChange(mapStatus: MapStatus?) {
            }

            override fun onMapStatusChangeFinish(mapStatus: MapStatus?) {
                val latLng = mapStatus?.target;
                mBaiduMap.clear();
                mHandler?.removeMessages(REVERSE_GEO_CODE)
                mHandler?.sendMessageDelayed(Message.obtain().apply {
                    what = REVERSE_GEO_CODE
                    obj = latLng
                }, DEFAULT_DELAYED)
            }

        })
    }

    private fun changeCenterLatLng(latitude: Double, longitude: Double) {
        if (latitude > 0.0 && longitude > 0.0) {
            mBaiduMap.animateMapStatus(
                MapStatusUpdateFactory.newLatLngZoom(
                    LatLng(
                        latitude,
                        longitude
                    ), 16f
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapview.onPause()
        if (isFinishing) {
            destroy()
        }
    }

    private fun destroy() {
        mapLocationManager?.onDestroy()
        mSuggestionSearch.destroy()
        mCoder.destroy()
        mapview.onDestroy()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.iv_search -> {
                et_search.setText("")
                editViewShow(true)
            }

            R.id.confirm_location -> {
                mPoiInfoModel?.let {
                    if ((it.latLng?.longitude ?: 0.0) <= 0.0 || (it.latLng?.latitude
                            ?: 0.0) <= 0.0
                    ) {
                        ToastUtils.showShort("数据异常，请重新选择！")
                        return@let
                    }
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putParcelable(
                        "poiInfo",
                        it
                    )
                    intent.putExtras(bundle)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }

            R.id.iv_cur_location -> {
                mBaiduMap.locationData?.run {
                    mHandler?.removeMessages(REVERSE_GEO_CODE)
                    mHandler?.sendMessageDelayed(Message.obtain().apply {
                        what = REVERSE_GEO_CODE
                        obj = LatLng(latitude, longitude)
                    }, DEFAULT_DELAYED)
                    changeCenterLatLng(latitude, longitude)
                }
            }

            else -> {
            }
        }
    }

    private fun editViewShow(isShow: Boolean) {
        val layoutParams = ll_search.layoutParams
        layoutParams?.run {
            width =
                if (isShow) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        ll_search.layoutParams = layoutParams
        et_search.visibility = if (isShow) View.VISIBLE else View.GONE
        et_search_city.visibility = if (isShow) View.VISIBLE else View.GONE
        if (!isShow) {
            KeyboardUtils.hideSoftInput(et_search)
            recycler.visibility = View.GONE
        }
    }


    class PickMapPoiHandler(activity: PickMapPoiActivity) : Handler(Looper.getMainLooper()) {
        private var mWeakReference: WeakReference<PickMapPoiActivity>? = null

        init {
            mWeakReference = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            mWeakReference?.get()?.let {
                when (msg.what) {
                    it.REVERSE_GEO_CODE -> {
                        it.reverseGeoCode(msg.obj as LatLng)
                    }

                    else -> {
                    }
                }
            }
        }
    }
}
package com.example.thirdsdk;

import java.util.ArrayList;
import java.util.List;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.AMapLocationClientOption.AMapLocationMode;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.AMap.OnMapClickListener;
import com.amap.api.maps2d.AMap.OnMarkerClickListener;
import com.amap.api.maps2d.model.BitmapDescriptor;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.api.maps2d.model.PolygonOptions;
import com.amap.api.maps2d.model.PolylineOptions;
import com.amap.api.maps2d.model.TextOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.core.SuggestionCity;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.amap.api.services.help.Inputtips.InputtipsListener;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.poisearch.PoiSearch.OnPoiSearchListener;
import com.amap.api.services.poisearch.PoiSearch.SearchBound;
import com.example.thirdsdk.util.DateUtil;
import com.example.thirdsdk.util.MapGaodeUtil;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import overlay.PoiOverlay;

/**
 * Created by ouyangshen on 2017/12/18.
 */
public class MapGaodeActivity extends AppCompatActivity implements OnClickListener,
        OnMapClickListener, OnPoiSearchListener, InputtipsListener {
    private static final String TAG = "MapGaodeActivity";
    private TextView tv_scope_desc, tv_loc_position;
    private int search_method;
    private String[] searchArray = {"???????????????", "???????????????"};
    private int SEARCH_CITY = 0;
    private int SEARCH_NEARBY = 1;
    private boolean isPaused = true;

    private void setMethodSpinner(Context context, int spinner_id, int seq) {
        Spinner sp_poi_method = findViewById(spinner_id);
        ArrayAdapter<String> county_adapter;
        county_adapter = new ArrayAdapter<String>(context,
                R.layout.item_select, searchArray);
        county_adapter.setDropDownViewResource(R.layout.item_select);
        // setPrompt?????????????????????????????????
        sp_poi_method.setPrompt("?????????POI????????????");
        sp_poi_method.setAdapter(county_adapter);
        sp_poi_method.setOnItemSelectedListener(new SpinnerSelectedListenerOrder());
        if (seq >= 0) {
            sp_poi_method.setSelection(seq, true);
        } else {
            sp_poi_method.setFocusable(false);
        }
    }

    class SpinnerSelectedListenerOrder implements OnItemSelectedListener {
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            search_method = arg2;
            if (search_method == SEARCH_CITY) {
                tv_scope_desc.setText("?????????");
            } else if (search_method == SEARCH_NEARBY) {
                tv_scope_desc.setText("?????????");
            }
            et_city.setText("");
            et_searchkey.setText("");
        }

        public void onNothingSelected(AdapterView<?> arg0) {}
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_gaode);
        tv_scope_desc = findViewById(R.id.tv_scope_desc);
        tv_loc_position = findViewById(R.id.tv_loc_position);
        setMethodSpinner(this, R.id.sp_poi_method, SEARCH_CITY);
        initLocation(savedInstanceState);
        initMap();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_search) {
            searchButtonProcess(v);
        } else if (v.getId() == R.id.btn_next_data) {
            goToNextPage(v);
        } else if (v.getId() == R.id.btn_clear_data) {
            et_city.setText("");
            et_searchkey.setText("");
            // ??????????????????
            mMapView.getMap().clear();
            posArray.clear();
            isPolygon = false;
        }
    }

    @Override
    public void onPause() {
        mMapView.onPause();
        isPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        if (isPaused) {
            mMapView.onResume();
            isPaused = false;
        }
        super.onResume();
    }

    @Override
    public void onDestroy() {
        // ?????????????????????
        if (null != mLocClient) {
            mLocClient.onDestroy();
            mLocClient = null;
        }
        // ??????????????????
        if (mMapLayer != null) {
            mMapLayer.setMyLocationEnabled(false);
        }
        mMapView.onDestroy();
        mMapView = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    private double mLatitude;
    private double mLongitude;

    // ???????????????POI?????????????????????
    private PoiSearch mPoiSearch = null;
    private AutoCompleteTextView et_searchkey = null;
    private EditText et_city = null;
    private ArrayAdapter<String> sugAdapter = null;
    private int load_Index = 0;

    private void initMap() {
        et_city = findViewById(R.id.et_city);
        et_searchkey = findViewById(R.id.et_searchkey);
        findViewById(R.id.btn_search).setOnClickListener(this);
        findViewById(R.id.btn_next_data).setOnClickListener(this);
        findViewById(R.id.btn_clear_data).setOnClickListener(this);
        sugAdapter = new ArrayAdapter<String>(this, R.layout.item_select);
        et_searchkey.setAdapter(sugAdapter);
        // ??????????????????????????????????????????????????????
        et_searchkey.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable arg0) {}

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

            @Override
            public void onTextChanged(CharSequence cs, int arg1, int arg2, int arg3) {
                if (cs.length() <= 0) {
                    return;
                }
                String city = et_city.getText().toString();
                // ??????????????????????????????????????????????????????onGetInputtips?????????
                InputtipsQuery inputquery = new InputtipsQuery(cs.toString(), city);
                Inputtips inputTips = new Inputtips(MapGaodeActivity.this, inputquery);
                inputTips.setInputtipsListener(MapGaodeActivity.this);
                inputTips.requestInputtipsAsyn();
            }
        });
    }

    @Override
    public void onGetInputtips(List<Tip> tipList, int rCode) {
        if (rCode != 1000) {
            Toast.makeText(this, "???????????????????????????" + rCode, Toast.LENGTH_LONG).show();
        } else {
            sugAdapter.clear();
            for (Tip info : tipList) {
                if (info.getName() != null) {
                    sugAdapter.add(info.getName());
                }
            }
            sugAdapter.notifyDataSetChanged();
        }
    }

    // ??????????????????????????????
    public void searchButtonProcess(View v) {
        Log.d(TAG, "editCity=" + et_city.getText().toString()
                + ", editSearchKey=" + et_searchkey.getText().toString()
                + ", load_Index=" + load_Index);
        String keyword = et_searchkey.getText().toString();
        if (search_method == SEARCH_CITY) {
            String city = et_city.getText().toString();
            PoiSearch.Query query = new PoiSearch.Query(keyword, null, city);
            query.setPageSize(10);
            query.setPageNum(load_Index);
            mPoiSearch = new PoiSearch(this, query);
            mPoiSearch.setOnPoiSearchListener(this);
            mPoiSearch.searchPOIAsyn();
        } else if (search_method == SEARCH_NEARBY) {
            LatLonPoint position = new LatLonPoint(mLatitude, mLongitude);
            int radius = Integer.parseInt(et_city.getText().toString());
            PoiSearch.Query query = new PoiSearch.Query(keyword, null, "??????");
            query.setPageSize(10);
            query.setPageNum(load_Index);
            mPoiSearch = new PoiSearch(this, query);
            SearchBound bound = new SearchBound(position, radius);
            mPoiSearch.setBound(bound);
            mPoiSearch.setOnPoiSearchListener(this);
            mPoiSearch.searchPOIAsyn();
        }
    }

    public void goToNextPage(View v) {
        load_Index++;
        searchButtonProcess(null);
    }

    @Override
    public void onPoiSearched(PoiResult result, int rCode) {
        if (rCode != 1000) {
            Toast.makeText(this, "POI???????????????" + rCode, Toast.LENGTH_LONG).show();
        } else if (result == null || result.getQuery() == null) {
            Toast.makeText(this, "???????????????", Toast.LENGTH_LONG).show();
        } else {
            mMapLayer.clear();
            List<PoiItem> poiList = result.getPois();
            // ???????????????poiitem?????????????????????????????????????????????????????????
            List<SuggestionCity> suggestionCities = result.getSearchSuggestionCitys();
            if (poiList != null && poiList.size() > 0) {
                PoiOverlay poiOverlay = new PoiOverlay(mMapLayer, poiList);
                // ?????????????????????POI??????
                poiOverlay.removeFromMap();
                // ??????????????????POI??????
                poiOverlay.addToMap();
                poiOverlay.zoomToSpan();
                // ???POI???????????????????????????POI?????????POI??????
                mMapLayer.setOnMarkerClickListener(new OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        marker.showInfoWindow();
                        return true;
                    }
                });
            } else if (suggestionCities != null && suggestionCities.size() > 0) {
                String infomation = "????????????\n";
                for (int i = 0; i < suggestionCities.size(); i++) {
                    SuggestionCity city = suggestionCities.get(i);
                    infomation += "????????????:" + city.getCityName() + "????????????:"
                            + city.getCityCode() + "????????????:" + city.getAdCode() + "\n";
                }
                Toast.makeText(this, infomation, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "??????????????????0", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onPoiItemSearched(PoiItem paramPoiItem, int paramInt) {}

    // ????????????????????????????????????
    private MapView mMapView; // ??????????????????????????????
    private AMap mMapLayer; // ??????????????????????????????
    private AMapLocationClient mLocClient; // ?????????????????????????????????
    private boolean isFirstLoc = true; // ??????????????????

    // ?????????????????????
    private void initLocation(Bundle savedInstanceState) {
        // ??????????????????????????????amapView???????????????
        mMapView = findViewById(R.id.amapView);
        // ?????????????????????????????????
        mMapView.onCreate(savedInstanceState);
        // ??????????????????????????????????????????????????????
        mMapView.setVisibility(View.INVISIBLE);
        if (mMapLayer == null) {
            mMapLayer = mMapView.getMap(); // ????????????????????????????????????
        }
        mMapLayer.setOnMapClickListener(this); // ??????????????????????????????????????????
        mMapLayer.setMyLocationEnabled(true); // ??????????????????
        mLocClient = new AMapLocationClient(this.getApplicationContext()); // ???????????????????????????
        mLocClient.setLocationListener(new MyLocationListenner()); // ?????????????????????
        AMapLocationClientOption option = new AMapLocationClientOption(); // ????????????????????????
        option.setLocationMode(AMapLocationMode.Battery_Saving); // ???????????????????????????
        option.setNeedAddress(true); // ??????true?????????????????????????????????
        mLocClient.setLocationOption(option); // ????????????????????????????????????
        mLocClient.startLocation(); // ?????????????????????????????????
        // ???????????????????????????
        // mLocClient.getLastKnownLocation();
    }

    // ???????????????????????????
    public class MyLocationListenner implements AMapLocationListener {

        // ?????????????????????????????????
        public void onLocationChanged(AMapLocation location) {
            // ??????????????????????????????????????????????????????????????????
            if (location == null || mMapView == null) {
                Log.d(TAG, "location is null or mMapView is null");
                return;
            }
            mLatitude = location.getLatitude(); // ????????????????????????
            mLongitude = location.getLongitude(); // ????????????????????????
            String position = String.format("???????????????%s|%s|%s|%s|%s|%s|%s",
                    location.getProvince(), location.getCity(),
                    location.getDistrict(), location.getStreet(),
                    location.getStreetNum(), location.getAddress(),
                    DateUtil.formatDate(location.getTime()));
            tv_loc_position.setText(position);
            if (isFirstLoc) { // ????????????
                isFirstLoc = false;
                LatLng ll = new LatLng(mLatitude, mLongitude); // ???????????????????????????
                CameraUpdate update = CameraUpdateFactory.newLatLngZoom(ll, 12);
                mMapLayer.moveCamera(update); // ????????????????????????????????????????????????
                mMapView.setVisibility(View.VISIBLE); // ???????????????????????????????????????
                // ???????????????
                BitmapDescriptor bitmapDesc = BitmapDescriptorFactory
                        .fromResource(R.drawable.icon_locate);
                MarkerOptions ooMarker = new MarkerOptions().draggable(false)
                        .visible(true).icon(bitmapDesc).position(ll);
                mMapLayer.addMarker(ooMarker);
            }
        }
    }

    // ???????????????????????????????????????
    private static int lineColor = 0x55FF0000;
    private static int arcColor = 0xbb00FFFF;
    private static int textColor = 0x990000FF;
    private static int polygonColor = 0x77FFFF00;
    private static int radius = 100;
    private ArrayList<LatLng> posArray = new ArrayList<LatLng>();
    boolean isPolygon = false;

    private void addDot(LatLng pos) {
        if (isPolygon && posArray.size() > 1 && MapGaodeUtil.isInsidePolygon(pos, posArray)) {
            Log.d(TAG, "isInsidePolygon");
            LatLng centerPos = MapGaodeUtil.getCenterPos(posArray);
            TextOptions ooText = new TextOptions().backgroundColor(0x00ffffff)
                    .fontSize(26).fontColor(textColor).text("??????")// .rotate(-30)
                    .position(centerPos);
            mMapLayer.addText(ooText);
            return;
        }
        if (isPolygon) {
            Log.d(TAG, "isPolygon == true");
            posArray.clear();
            isPolygon = false;
        }
        boolean is_first = false;
        LatLng thisPos = pos;
        if (posArray.size() > 0) {
            LatLng firstPos = posArray.get(0);
            int distance = (int) Math.round(MapGaodeUtil.getShortDistance(
                    thisPos.longitude, thisPos.latitude, firstPos.longitude,
                    firstPos.latitude));
            // ?????????????????????????????????
            if (posArray.size() == 1 && distance <= 0) {
                return;
            } else if (posArray.size() > 1) {
                LatLng lastPos = posArray.get(posArray.size() - 1);
                int lastDistance = (int) Math.round(MapGaodeUtil.getShortDistance(
                        thisPos.longitude, thisPos.latitude, lastPos.longitude,
                        lastPos.latitude));
                // ????????????????????????????????????????????????
                if (lastDistance <= 0) {
                    return;
                }
            }
            if (distance < radius * 2) {
                thisPos = firstPos;
                is_first = true;
            }
            Log.d(TAG, "distance=" + distance + ", radius=" + radius
                    + ", is_first=" + is_first);

            // ?????????
            LatLng lastPos = posArray.get(posArray.size() - 1);
            List<LatLng> points = new ArrayList<LatLng>();
            points.add(lastPos);
            points.add(thisPos);
            PolylineOptions ooPolyline = new PolylineOptions().width(2)
                    .color(lineColor).addAll(points);
            mMapLayer.addPolyline(ooPolyline);

            // ??????????????????????????????
            distance = (int) Math.round(MapGaodeUtil.getShortDistance(
                    thisPos.longitude, thisPos.latitude, lastPos.longitude,
                    lastPos.latitude));
            String disText;
            if (distance > 1000) {
                disText = Math.round(distance * 10 / 1000) / 10d + "??????";
            } else {
                disText = distance + "???";
            }
            LatLng llText = new LatLng(
                    (thisPos.latitude + lastPos.latitude) / 2,
                    (thisPos.longitude + lastPos.longitude) / 2);
            TextOptions ooText = new TextOptions().backgroundColor(0x00ffffff)
                    .fontSize(24).fontColor(textColor).text(disText)// .rotate(-30)
                    .position(llText);
            mMapLayer.addText(ooText);
        }
        if (!is_first) {
//			// ?????????
//			CircleOptions ooCircle = new CircleOptions().fillColor(lineColor)
//					.center(thisPos).strokeWidth(2).strokeColor(0xAAFF0000)
//					.radius(radius);
//			mMapLayer.addCircle(ooCircle);
            // ???????????????
            BitmapDescriptor bitmapDesc = BitmapDescriptorFactory
                    .fromResource(R.drawable.icon_geo);
            MarkerOptions ooMarker = new MarkerOptions().draggable(false)
                    .visible(true).icon(bitmapDesc).position(thisPos);
            mMapLayer.addMarker(ooMarker);
            mMapLayer.setOnMarkerClickListener(new OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker) {
                    LatLng markPos = marker.getPosition();
                    addDot(markPos);
                    marker.showInfoWindow();
                    return true;
                }
            });
        } else {
            Log.d(TAG, "posArray.size()=" + posArray.size());
            // ????????????????????????????????????????????????????????????
            if (posArray.size() < 3) {
                posArray.clear();
                isPolygon = false;
                return;
            }
            // ????????????
            PolygonOptions ooPolygon = new PolygonOptions().addAll(posArray)
                    .strokeColor(0xFF00FF00).strokeWidth(1)
                    .fillColor(polygonColor);
            mMapLayer.addPolygon(ooPolygon);
            isPolygon = true;

            // ??????????????????????????????
            LatLng centerPos = MapGaodeUtil.getCenterPos(posArray);
            double area = Math.round(MapGaodeUtil.getArea(posArray));
            String areaText;
            if (area > 1000000) {
                areaText = Math.round(area * 100 / 1000000) / 100d + "????????????";
            } else {
                areaText = (int) area + "?????????";
            }
            TextOptions ooText = new TextOptions().backgroundColor(0x00ffffff)
                    .fontSize(26).fontColor(textColor).text(areaText)// .rotate(-30)
                    .position(centerPos);
            mMapLayer.addText(ooText);
        }
        posArray.add(thisPos);
    }

    @Override
    public void onMapClick(LatLng arg0) {
        addDot(arg0);
    }

}

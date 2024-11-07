package com.example.routecast2;
import  android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.codebyashish.googledirectionapi.RouteInfoModel;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import com.google.gson.JsonParser;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsStep;
import com.google.maps.model.Duration;


import org.json.JSONException;
import org.json.JSONObject;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnPolylineClickListener {
    GoogleMap map;
    FusedLocationProviderClient client;
    double userLat,userLng;
    LatLng destinationLocation,userLocation;
    List<LatLng> points = new ArrayList<>();
    AutocompleteSupportFragment autocompleteSupportFragment;
    String mapsApiKey = "AIzaSyAwjC5bEPFGPXgfby2gqiCYiAITS6-qECY" ;//
    String WeatherApiKey = "1b1ab26218b443c5afb142550241508";
    ArrayList<RouteInfoModel> list2;
    GeoApiContext mGeoApiContext = null;
    ArrayList<PolylineData> mPolylineData = new ArrayList<>();
    Duration finalArrivalDuration,arrivalTime;
    DirectionsResult polylineRoute;
    boolean firstTime = true;

    DirectionsStep[] ds;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Places.initialize(getApplicationContext(),mapsApiKey);
        PlacesClient placesClient = Places.createClient(this);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        autocompleteSupportFragment = (AutocompleteSupportFragment)getSupportFragmentManager()
                .findFragmentById(R.id.autoCompleteFragment);

        assert autocompleteSupportFragment != null;

        autocompleteSupportFragment.setLocationBias(RectangularBounds.newInstance(
                new LatLng(-33.880490, 151.184363),
                new LatLng(-33.858754,151.229596)));
        autocompleteSupportFragment.setCountries("IN");

        autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.NAME,Place.Field.ID,Place.Field.LOCATION));
        autocompleteSupportFragment.setHint("Search Location");

        autocompleteSupportFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                map.clear();
                destinationLocation = place.getLocation();
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromDrawable(R.drawable.maps_and_flags)));
                markerOptions.position(destinationLocation);
                Marker m = map.addMarker(markerOptions);
                firstTime = true;

                assert m != null;
                calculateDirections(m);
            }

            @Override
            public void onError(@NonNull Status status) {
                Log.i("place",status.toString());
            }
        });

        assert mapFragment != null;
        mapFragment.getMapAsync(this);
        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder().apiKey(mapsApiKey).build();
        }

        client = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        map.setMyLocationEnabled(true);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnPolylineClickListener(this);

        fetchMyLocation();
    }

    private void fetchMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission
                (this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Task<Location> task = client.getLastLocation();

        task.addOnSuccessListener(new OnSuccessListener<Location>(){
            @Override
            public void onSuccess(Location location) {
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                userLocation = new LatLng(userLat,userLng);

                CameraPosition cameraPosition = new CameraPosition.Builder().target(userLocation).zoom(12).build();
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                //currentWeather(location);

            }
        });
    }

    private void calculateDirections(Marker marker){

        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
                marker.getPosition().latitude,
                marker.getPosition().longitude
        );
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);

        directions.alternatives(true);
        directions.origin(new com.google.maps.model.LatLng(
                userLocation.latitude,
                userLocation.longitude
        ));
        directions.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d("d25", "calculateDirections: routes: " + result.routes[0].toString());
                Log.d("d25", "calculateDirections: duration: " + result.routes[0].legs[0].duration);
                Log.d("d25", "calculateDirections: distance: " + result.routes[0].legs[0].distance);
                Log.d("d25", "calculateDirections: geocodedWayPoints: " + result.geocodedWaypoints[0].toString());
                polylineRoute=result;

                addPolyLinesToMap(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e("d26", "calculateDirections: Failed to get directions: " + e.getMessage() );

            }
        });
    }

    private void addPolyLinesToMap(final DirectionsResult result){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Log.d("d28", "run: result routes: " + result.routes.length);
                if(!mPolylineData.isEmpty()){
                        for(PolylineData polylineData: mPolylineData) {
                            polylineData.getPolyline().remove();
                        }
                        mPolylineData.clear();
                        mPolylineData =new ArrayList<>();
                }
                double duration = 999999999;
                for(DirectionsRoute route: result.routes){
                    Log.d("d29", "run: leg: " + route.legs[0].toString());
                    List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());

                    List<LatLng> newDecodedPath = new ArrayList<>();
                    points = new ArrayList<>();

                    // This loops through all the LatLng coordinates of ONE polyline.
                    for(com.google.maps.model.LatLng latLng: decodedPath){
                        Log.d("d30", "run: latlng: " + latLng.toString());

                        newDecodedPath.add(new LatLng(
                                latLng.lat,
                                latLng.lng
                        ));
                    }
                    points.addAll(newDecodedPath);

                    Polyline polyline = map.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                    polyline.setColor(ContextCompat.getColor(getApplicationContext(),R.color.darkGrey));
                    polyline.setClickable(true);
                    mPolylineData.add(new PolylineData(polyline,route.legs[0]));

                    double tempDuration = route.legs[0].duration.inSeconds;
                    if(tempDuration < duration) {
                        duration = tempDuration;
                        onPolylineClick(polyline);
                    }
                }
            }
        });
    }

    @Override
    public void onPolylineClick(Polyline polyline) {
//        map.clear();
//        MarkerOptions markerOptions = new MarkerOptions();
//        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromDrawable(R.drawable.maps_and_flags)));
//        markerOptions.position(destinationLocation);
//        Marker m = map.addMarker(markerOptions);
//        addPolyLinesToMap(polylineRoute);

        long distance = 0;
        for (PolylineData polylineData : mPolylineData) {
            Log.d("d31", "onPolylineClick: toString: " + polylineData.toString());
            if (polyline.getId().equals(polylineData.getPolyline().getId())) {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getApplicationContext(), R.color.blue));
                polylineData.getPolyline().setZIndex(1);
                ds=polylineData.getLeg().steps;
                finalArrivalDuration = polylineData.getLeg().duration;
                distance = polylineData.getLeg().distance.inMeters;
            } else {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getApplicationContext(), R.color.darkGrey));
                polylineData.getPolyline().setZIndex(0);

            }
        }
        weatherMarkerBiasSelectorDS(distance);

    }

    private Bitmap getBitmapFromDrawable(int resId) {
        Bitmap bitmap = null;
        Drawable drawable = ResourcesCompat.getDrawable(getResources(),resId,null );
        if (drawable != null){
            bitmap = Bitmap.createBitmap(120 , 120 , Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0,0,canvas.getWidth(),canvas.getHeight());
            drawable.draw(canvas);
        }
        return bitmap;
    }

    public void currentWeather(Location intermediateLocation) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String intermediateLocationLat = String.valueOf(intermediateLocation.getLatitude());
        String intermediateLocationLon = String.valueOf(intermediateLocation.getLongitude());
        Log.e("d2", "weather: "+intermediateLocationLat+","+intermediateLocationLon);

        //add duration(converted to time) to the url

        String url = MessageFormat.format("https://api.weatherapi.com/v1/current.json?key={0}&q={1},{2}&aqi=no",
                WeatherApiKey,intermediateLocationLat,intermediateLocationLon);

        StringRequest stringRequest = new StringRequest(Request.Method.GET,url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.e("response_api", "onResponse: " + response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("response_api", "onErrorResponse: "+error.getMessage() );
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    public void forecastWeather(int time,LatLng intermediateLocation) {

        //todo
        // 1 use regex to get the image link
        // 2 get device time from user device add time to the this
        // 3 get the image and place it in the marker

        RequestQueue queue = Volley.newRequestQueue(this);
        String intermediateLocationLat = String.valueOf(intermediateLocation.latitude);
        String intermediateLocationLon = String.valueOf(intermediateLocation.longitude);
        Log.e("d2", "weather: "+intermediateLocationLat+","+intermediateLocationLon);

        //add duration(converted to time) to the url

        String url = MessageFormat.format("https://api.weatherapi.com/v1/forecast.json?key={0}&q={1},{2}&aqi=no",
                WeatherApiKey,intermediateLocationLat,intermediateLocationLon);


        StringRequest stringRequest = new StringRequest(Request.Method.GET,url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        // take the response from the response string and extract the image link
                        // use the lat,long from the response and place marker on the route and show the weather

                        //todo
                        // 1 use regex to get the image link
                        // 2 get the location from the weatherMarker function
                        // 3 delete the add marker function from the weatherMarker function and place it here

                        Log.e("response_api", "onResponse: " + response);

                        try {
                            if(time<25) {
                                JSONObject json = new JSONObject(response);
                                String icon = (String) json.getJSONObject("forecast").getJSONArray("forecastday").
                                        getJSONObject(0).getJSONArray("hour").getJSONObject(time).
                                        getJSONObject("condition").get("icon");

                                addMarkerFromForecastWeather(icon, time, intermediateLocation);
                            }else {
                                addMarkerFromForecastWeather(response,time,intermediateLocation);
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }


                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("response_api", "onErrorResponse: "+error.getMessage() );
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

//    public void weatherMarker(int mainBias,int secondaryBias){
//        int c = mainBias;
//        for (int j=0;j<points.size();j++){
//            if (c==0) {
//                if(points.size()-j<mainBias) {
//                    break;
//                }
//                else {
////                    Marker m = map.addMarker(new MarkerOptions().position(points.get(j)).visible(false));
////                    assert m != null;
////                    calculateTime(m);
//                    Duration time = arrivalTime;//null is returned
//                    Log.d("d32", "weatherMarker: time: " + time);
////                    long timeInSecs = time.inSeconds;
////                    Date currentTime = Calendar.getInstance().getTime();
////                    int timeInSecs2 = (int) (timeInSecs);
////                    int timeInMinutes = timeInSecs2/60;
////                    int timeInHours = timeInMinutes/60;
////                    int timeInDays = timeInHours/24;
////                    int currentHour = currentTime.getHours();
////                    int arrivalTimeInHours = timeInHours+currentHour;
////                    if(timeInDays>0){
////                        continue;
////                    }
//
//                    //forecastWeather(arrivalTimeInHours,points.get(j));
//
//                    map.addMarker(new MarkerOptions().position(points.get(j)));
//                    c = secondaryBias;
//                }
//            }
//            c--;
//        }
//    }
//
//    public void weatherMarker(){
//        for (int j=0;j<points.size();j++){
//            if(points.size()-j==1){
//                break;
//            }
//            int arrivalTimeInHours = 0;
//            //forecastWeather(arrivalTimeInHours,points.get(j));
//            map.addMarker(new MarkerOptions().position(points.get(j)));
//        }
//    }

//    public void weatherMarkerBiasSelector(long distance){
//        Log.d("d33","ds length "+ds.length);
//        Log.d("d33","points size "+points.size());
//        if(distance<50000) {
//            weatherMarker(100, 50);
//        } else if (distance<300000) {
//            weatherMarker(10, 10);
//        } else{
//            weatherMarker();
//        }
//    }



    public void addMarkerFromForecastWeather(String response,int time,LatLng intermediateLocation){
        //todo
        // add custom marker
        Log.d("d99", "addMarkerFromForecastWeather: "+response+"\t"+time+"\t"+intermediateLocation);
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromDrawable(R.drawable.sunny)));
        markerOptions.position(intermediateLocation);
        map.addMarker(markerOptions);
    }



    public void weatherMarkerDS(int mainBias,int secondaryBias){
        int[] arrivalTimeInMin = new int[1000];
        Map<LatLng , Integer> dsMap = new HashMap<>();
        for (DirectionsStep d : ds) {
            com.google.maps.model.LatLng ll2 = d.endLocation;
            double latitude , longitude;
            latitude = roundToDecimalPlaces(ll2.lat,4);
            longitude = roundToDecimalPlaces(ll2.lng,4);
            LatLng dsLL = new LatLng(latitude, longitude);
            Log.d("d59", "dsMap LL "+ dsLL+"\t");
            dsMap.put(dsLL, (int) (d.duration.inSeconds));
            Log.d("d69", "dsMap "+ dsMap.get(dsLL));
        }
        int c = mainBias ,i = 0 ,j = 0;
        for (int k=0;k<points.size();k++) {
            LatLng ll;
            double latitude2,longitude2;
            latitude2 = roundToDecimalPlaces(points.get(k).latitude,4);
            longitude2 = roundToDecimalPlaces(points.get(k).longitude,4);
            ll = new LatLng(latitude2,longitude2);

            if (dsMap.containsKey(ll)) { //todo this is never equal bc
                arrivalTimeInMin[i+1] = arrivalTimeInMin[i] + dsMap.get(ll);
                Log.d("d39", "weatherMarker: time: " + arrivalTimeInMin[i]);
                i++;
            }
            if (c == 0) {
                if (points.size() - k < mainBias) {
                    break;
                } else {
                    c = secondaryBias;

                    Log.d("d49", "weatherMarker: time: " + arrivalTimeInMin[j]);
                    Log.d("d59", "points LL : "+ points.get(k)+"\t");
                    j++;

                    int timeToGive = roundTime(arrivalTimeInMin[i]);
                    Log.d("d66", "weatherMarkerDS: "+ timeToGive );
                    int currentTime = Calendar.getInstance().getTime().getHours();
                    Log.d("d68", "weatherMarkerDS: "+ currentTime );
                    timeToGive = currentTime +timeToGive;
                    Log.d("d67", "weatherMarkerDS: "+ timeToGive );
                    forecastWeather(timeToGive,points.get(k));
                }
            }
            c--;
        }
    }

    public void weatherMarkerDS(){
        int[] arrivalTimeInMin = new int[1000] , dsArray = new int[1000];
        arrivalTimeInMin[0] = 0;
        int i = 0;
        for (DirectionsStep d : ds) {
            com.google.maps.model.LatLng ll2 = d.endLocation;
            dsArray[i] = (int) (d.duration.inSeconds);
            Log.d("d59", "dsArray "+ dsArray[i]);
            i++;
        }
        int markerNumber = (int) (dsArray.length/points.size()); // for how many markers will use same time

        int j=0;
        i=0;
        for (int k=0;k<points.size();k++) {
            if (j==markerNumber) {
                arrivalTimeInMin[i+1]=dsArray[i+1]+arrivalTimeInMin[i];
                i++;
                j=0;
            }
            Log.d("d69", "weatherMarkerDS: "+ arrivalTimeInMin[i] );
            int timeToGive = roundTime(arrivalTimeInMin[i]);
            int currentTime = (int)(Calendar.getInstance().getTimeInMillis()/3600000);
            Log.d("d68", "weatherMarkerDS: "+ currentTime );
            timeToGive = currentTime +timeToGive;
            Log.d("d67", "weatherMarkerDS: "+ timeToGive );
            forecastWeather(timeToGive,points.get(k));
            j++;
        }
    }

//Map<Integer , Integer> arrivalTimeInMin = new HashMap<>();
//        int[] time = new int[1000];
//        arrivalTimeInMin.put(0,0);
//        Map<LatLng , Integer> dsMap = new HashMap<>();
//        int j=0;
//        for (DirectionsStep d : ds) {
//            com.google.maps.model.LatLng ll2 = d.endLocation;
//            double latitude , longitude;
//            latitude = roundToDecimalPlaces(ll2.lat,2);
//            longitude = roundToDecimalPlaces(ll2.lng,2);
//            LatLng dsLL = new LatLng(latitude, longitude);
//            Log.d("d59", "dsMap LL "+ dsLL+"\t");
//            dsMap.put(dsLL, (int) (d.duration.inSeconds));
//            time[j]= (int) (d.duration.inSeconds);
//            j++;
//            Log.d("d69", "dsMap "+ dsMap.get(dsLL));
//        }
//        int i = 0,totalTime = 0;
//        j=0;
//        for (int k=0;k<points.size();k++) {
//            LatLng ll;
//            double latitude2,longitude2;
//            latitude2 = roundToDecimalPlaces(points.get(k).latitude,2);
//            longitude2 = roundToDecimalPlaces(points.get(k).longitude,2);
//            ll = new LatLng(latitude2,longitude2);
//            totalTime = totalTime + time[j];
//            j++;
//            if (dsMap.containsKey(ll)) { //stores the time in sec
//                arrivalTimeInMin.put(i+1,totalTime + dsMap.get(ll)) ;
//                Log.d("d39", "weatherMarker: time: " + arrivalTimeInMin.get(i));
//                i++;
//            }
//            map.addMarker(new MarkerOptions().position(points.get(k)));
//            Log.d("d49", "weatherMarker: time: " + arrivalTimeInMin.get(i));
//            Log.d("d59", "points LL : "+ points.get(k)+"\t");
//            //forecastWeather(arrivalTimeInMin[j]/60,points.get(k));
//        }
    public void weatherMarkerBiasSelectorDS(long distance){
        //todo
        // take some % of LatLng from points depending on the distance
        // and then divide the array into that many sub array
        // and take the first or last value of the sub array
        if(distance<50000) {
            weatherMarkerDS(100, 50);
        } else if (distance<300000) {
            weatherMarkerDS(10, 10);
        } else{
            weatherMarkerDS();
        }
    }

    public double roundToDecimalPlaces(double value,int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    public int roundTime(int time){
        if(time%60>=50){
            time = (time/60)+1;
            if(time%60>=50){
                time = (time/60)+1;
            }else {
                time = (time/60);
            }
        }else {
            time = (time/60);
            if(time%60>=50){
                time = (time/60)+1;
            }else {
                time = (time/60);
            }
        }
        return time;
    }




//    private void calculateTime(Marker marker){
//
//        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
//                marker.getPosition().latitude,
//                marker.getPosition().longitude
//        );
//        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);
//
//        directions.alternatives(true);
//        directions.origin(new com.google.maps.model.LatLng(
//                userLocation.latitude,
//                userLocation.longitude
//        ));
//        directions.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
//            @Override
//            public void onResult(DirectionsResult result) {
//                Log.d("d45","arrival time"+result.routes[0].legs[0].duration);
//                arrivalTime  = result.routes[0].legs[0].duration;
//                //gives the duration of the estimate arrival time to the marker
//
//            }
//
//            @Override
//            public void onFailure(Throwable e) {
//                Log.e("d26", "calculateDirections: Failed to get directions: " + e.getMessage() );
//
//            }
//        });
//    }


//    private void weatherMarkerBiasSelector() {
//        Log.d("d33","ds length "+ds.length);
////        for(DirectionsStep d:ds){
////            LatLng ll = new LatLng(d.endLocation.lat,d.endLocation.lng);
////            Log.d("d33","ll "+d.distance+" , "+ Arrays.toString(d.steps) +" , "+d.duration);
//////            forecastWeather(ll);
////            map.addMarker(new MarkerOptions().position(ll));
////        }
//        Log.d("d33","points length "+points.size());
//        for(LatLng ll:points){
//            map.addMarker(new MarkerOptions().position(ll));
//        }
//    }






}
/*
map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull LatLng latLng) {
                dialog.setMessage("Please Wait");
                dialog.show();
                map.clear();
                destinationLocation = latLng;
                MarkerOptions markerOptions = new MarkerOptions();
//                markerOptions.icon(setIcon(MainActivity.this , R.drawable.maps_and_flags));
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromDrawable(R.drawable.maps_and_flags)));
                markerOptions.position(destinationLocation);
                map.addMarker(markerOptions);

                getRoute(userLocation,destinationLocation);
            }
        });
*/

//todo
// 2 get time wise weather in weather forecast and place markers accordingly
// 3 get the Latlng for weather forecast method from route
// 4 make a function to add marker only and one to equate ds and points
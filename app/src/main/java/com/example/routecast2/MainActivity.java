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
import android.widget.Toast;

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

import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsStep;


import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnPolylineClickListener {
    GoogleMap map;
    FusedLocationProviderClient client;
    double userLat,userLng;
    LatLng destinationLocation,userLocation;
    List<LatLng> points;
    AutocompleteSupportFragment autocompleteSupportFragment;
    String mapsApiKey = "AIzaSyAwjC5bEPFGPXgfby2gqiCYiAITS6-qECY" ;//
    String WeatherApiKey = "1b1ab26218b443c5afb142550241508";
    ArrayList<RouteInfoModel> list2;
    GeoApiContext mGeoApiContext = null;
    ArrayList<PolylineData> mPolylineData = new ArrayList<>();


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

                currentWeather(location);

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

                    // This loops through all the LatLng coordinates of ONE polyline.
                    for(com.google.maps.model.LatLng latLng: decodedPath){
                        Log.d("d30", "run: latlng: " + latLng.toString());

                        newDecodedPath.add(new LatLng(
                                latLng.lat,
                                latLng.lng
                        ));
                        //points.set(0, new LatLng(latLng.lat, latLng.lng));
                    }
                    Polyline polyline = map.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                    polyline.setColor(ContextCompat.getColor(getApplicationContext(),R.color.darkGrey));
                    polyline.setClickable(true);
                    mPolylineData.add(new PolylineData(polyline,route.legs[0]));

                    double tempDuration = route.legs[0].duration.inSeconds;
                    if(tempDuration < duration){
                        duration = tempDuration;
                        onPolylineClick(polyline);
                    }

                }
            }
        });
    }

    @Override
    public void onPolylineClick(Polyline polyline) {
        for (PolylineData polylineData : mPolylineData) {
            Log.d("d31", "onPolylineClick: toString: " + polylineData.toString());
            if (polyline.getId().equals(polylineData.getPolyline().getId())) {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getApplicationContext(), R.color.blue));
                polylineData.getPolyline().setZIndex(1);
//                ds=polylineData.getStep();
            } else {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getApplicationContext(), R.color.darkGrey));
                polylineData.getPolyline().setZIndex(0);

            }
        }
        //weatherMarkerBiasSelector();
    }

    private void weatherMarkerBiasSelector() {
        Log.d("d33","polyline length "+mPolylineData.size());
        Log.d("d33","ds length "+ds.length);
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

    public void forecastWeather(LatLng intermediateLocation) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String intermediateLocationLat = String.valueOf(intermediateLocation.latitude);
        String intermediateLocationLon = String.valueOf(intermediateLocation.longitude);
        Log.e("d2", "weather: "+intermediateLocationLat+","+intermediateLocationLon);


        String url = MessageFormat.format("https://api.weatherapi.com/v1/forecast.json?key={0}&q={1},{2}&aqi=no",
                WeatherApiKey,intermediateLocationLat,intermediateLocationLon);

        StringRequest stringRequest = new StringRequest(Request.Method.GET,url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        // take the response from the response string and extract the image link
                        // use the lat,long from the response and place marker on the route and show the weather

                        //todo response.indexOf();

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

    public void weatherMarker(int mainBias,int secondaryBias){
        int c = mainBias;
        for (int j=0;j<points.size();j++){
            if (c==0) {
                if(points.size()-j<100) {
                    break;
                }
                else {
                    //forecastWeather(points.get(j));
                    map.addMarker(new MarkerOptions().position(points.get(j)));
                    c = secondaryBias;
                }
            }
            c--;
        }
    }

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
// 1 create a separate method for current weather and weather forecast
// 2 get time wise weather in weather forecast and place markers accordingly
// 3 get the Latlng for weather forecast method from route
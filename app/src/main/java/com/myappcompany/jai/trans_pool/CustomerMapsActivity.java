package com.myappcompany.jai.cab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;


public class CustomerMapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener
{

    Location currentLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    private static final int REQUEST_CODE = 101;
    private GoogleMap mMap;
    GoogleApiClient googleApiClient;
    Location lastLocation;
    LocationRequest locationRequest;
    private Button LogoutCustomerButton;
    private Button SettingsCustomerButton;
    private Button CallCabButton;
    private String CustomerID;
    private LatLng CustomerPickupLocation;
    private LatLng CustomerDropLocation;
    private int radius= 1;
    private boolean driverFound = false;
    private String driverFoundID;
    private EditText load_text_kg;
    private boolean load_entered=false;

    private TextView txtName,txtPhone;
    private CircleImageView profilePic;
    private RelativeLayout relativeLayout;

    private DatabaseReference CustomerDatabaseRef;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    Marker DriverMarker;
    private Boolean currentLogoutCustomerStatus = false;
    private DatabaseReference DriverAvailableRef;
    private DatabaseReference DriversRef;
    private DatabaseReference DriverLocationRef;
    private String l="";





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_maps);


        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        CustomerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        CustomerDatabaseRef = FirebaseDatabase.getInstance().getReference().child("Customer Requests");
        DriverAvailableRef = FirebaseDatabase.getInstance().getReference().child("Driver's Available");
        DriverLocationRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");

        LogoutCustomerButton = (Button) findViewById(R.id.customer_logout_btn);
        SettingsCustomerButton = (Button) findViewById(R.id.customer_settings_btn);
        CallCabButton = (Button) findViewById(R.id.customer_call_cab_btn);

        txtName=findViewById(R.id.name_driver);
        txtPhone=findViewById(R.id.phone_driver);
        relativeLayout=findViewById(R.id.rel1);
        relativeLayout.setVisibility(View.INVISIBLE);
        profilePic=findViewById(R.id.profile_image_driver);



        load_text_kg=(EditText)findViewById(R.id.load_text);


      //  load_text_kg.setVisibility(View.INVISIBLE);



        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLastLocation();

        SettingsCustomerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(CustomerMapsActivity.this,SettingsActivity.class);
                intent.putExtra("type","Customers");
                startActivity(intent);

            }
        });

        LogoutCustomerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                currentLogoutCustomerStatus = true;
                final DatabaseReference sample=FirebaseDatabase.getInstance().getReference();
                sample.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                    {
                        if(dataSnapshot.child("Customer Requests").hasChild(CustomerID))
                        {

                                sample.child("Customer Requests").child(CustomerID).removeValue();


                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });



                mAuth.signOut();
                LogoutCustomer();
            }
        });

        CallCabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                String s = load_text_kg.getText().toString();
                if(s.equals(l))
                {
                    Toast.makeText(CustomerMapsActivity.this,"Please enter the load",Toast.LENGTH_SHORT).show();
                }

                else
                    {
                    load_text_kg.setVisibility(View.INVISIBLE);
                    GeoFire geoFire = new GeoFire(CustomerDatabaseRef);
                    geoFire.setLocation(CustomerID, new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {

                        }
                    });
                    CustomerPickupLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                    mMap.addMarker(new MarkerOptions().position(CustomerPickupLocation).title("Pickup Customer From Here"));

                    CallCabButton.setText("Getting your Driver...");
                    l=s;
                    GetClosestDriverCab();
                }
            }
        });


    }

    private void GetClosestDriverCab()
    {
        GeoFire geoFire = new GeoFire(DriverAvailableRef);
        final GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(CustomerPickupLocation.latitude,CustomerPickupLocation.longitude),radius);
        geoQuery.removeAllListeners();


        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(final String key, GeoLocation location)
            {







                if(!driverFound)
                {
                    final DatabaseReference loadref1=FirebaseDatabase.getInstance().getReference().child("Drivers load").child(key).child("Remaining Load Capacity");
                    loadref1.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                        {
                            String a = dataSnapshot.getValue().toString();

                            int b = Integer.parseInt(a);
                            int f_load = Integer.parseInt(l);
                            if(f_load>b)
                            {
                                Toast.makeText(CustomerMapsActivity.this, "remaining load capacity of " +
                                        "the nearest driver is :" + b, Toast.LENGTH_SHORT).show();



                                Toast.makeText(CustomerMapsActivity.this,"Searching for the next nearest driver",Toast.LENGTH_SHORT).show();
                                geoQuery.setRadius(radius+1);

                                GetClosestDriverCab();
                            }
                            else
                            {
                                driverFound = true;
                                driverFoundID = key;
                                final DatabaseReference loadref=FirebaseDatabase.getInstance().getReference().child("Drivers load").child(driverFoundID).child("Remaining Load Capacity");
                                loadref.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                                    {
                                        String e = dataSnapshot.getValue().toString();

                                        int p = Integer.parseInt(e);
                                        int fa_load = Integer.parseInt(l);
                                        int x = p-fa_load;
                                        loadref.setValue(x);
                                        Toast.makeText(CustomerMapsActivity.this,"remaining load capacity :"+x,Toast.LENGTH_SHORT).show();

                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError databaseError) {

                                    }
                                });
                                DatabaseReference CustomerAssigned = FirebaseDatabase.getInstance().getReference().child("Assigned Customers")
                                        .child("Drivers").child(driverFoundID).child(CustomerID);
                                CustomerAssigned.setValue(true);


                                DriversRef =FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                                HashMap driverMap = new HashMap();
                                driverMap.put("CustomerRideID",CustomerID);
                                DriversRef.updateChildren(driverMap);
                                GettingDriverLocation();
                                CallCabButton.setText("Looking for Driver Location...");

                            }


                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });






                }












            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady()
            {
                if(!driverFound)
                {
                    radius = radius+1;
                    GetClosestDriverCab();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void GettingDriverLocation()
    {
        DriverLocationRef.child(driverFoundID).child("l")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                    {
                        if(dataSnapshot.exists())
                        {
                            List<Object> driverLocationMap = (List<Object>) dataSnapshot.getValue();
                            double LocationLat = 0;
                            double LocationLng = 0;
                            CallCabButton.setText("Driver Found");

                            relativeLayout.setVisibility(View.VISIBLE);
                            getAssignedDriverInformation();

                            if(driverLocationMap.get(0)!= null)
                            {
                                LocationLat = Double.parseDouble(driverLocationMap.get(0).toString());


                            }
                            if(driverLocationMap.get(1)!= null)
                            {
                                LocationLng = Double.parseDouble(driverLocationMap.get(1).toString());


                            }
                            LatLng DriverLatLng = new LatLng(LocationLat,LocationLng);
                            if(DriverMarker != null)
                            {
                                DriverMarker.remove();
                            }

                            Location location1 = new Location("");
                            location1.setLatitude(CustomerPickupLocation.latitude);
                            location1.setLongitude((CustomerPickupLocation.longitude));

                            Location location2 = new Location("");
                            location2.setLatitude(DriverLatLng.latitude);
                            location2.setLongitude((DriverLatLng.longitude));

                            float Distance = location1.distanceTo(location2);
                            CallCabButton.setText("Driver Found : "+String.valueOf(Distance/1000)+" km");


                            DriverMarker = mMap.addMarker(new MarkerOptions().position(DriverLatLng).title("Your Driver is Here..."));


                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });


    }


    private void fetchLastLocation() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]
                    {Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE);
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(location != null){
                    currentLocation = location;
                    Toast.makeText(getApplicationContext(),currentLocation.getLatitude()+""+currentLocation.getLongitude(), Toast.LENGTH_SHORT).show();
                    SupportMapFragment supportMapFragment = (SupportMapFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.google_map);

                    LocationHelper helper = new LocationHelper(
                            location.getLongitude(),location.getLatitude()
                    );

                    /*FirebaseDatabase.getInstance().getReference("Current Location")
                            .setValue(helper).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if(task.isSuccessful()){
                                Toast.makeText(DriverMapsActivity.this,"Location Saved",Toast.LENGTH_SHORT);
                            }
                            else
                            {
                                Toast.makeText(DriverMapsActivity.this,"Location not saved",Toast.LENGTH_SHORT);
                            }
                        }
                    });

                     */

                    supportMapFragment.getMapAsync(CustomerMapsActivity.this);

                }
            }
        });

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        LatLng latLng = new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions().position(latLng)
                .title("I am here..");
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,18));
       // googleMap.addMarker(new MarkerOptions().position(latLng).title("I'm here"));
        mMap = googleMap;
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case REQUEST_CODE:
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    fetchLastLocation();

                }
                break;
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000);
        locationRequest.setPriority(locationRequest.PRIORITY_HIGH_ACCURACY);

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){

            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,locationRequest,this);


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(18));

       /*double latitude = location.getLatitude();
        double longitude = location.getLongitude();*/

       /* LocationHelper helper = new LocationHelper(
                location.getLongitude(),location.getLatitude()
        );

        FirebaseDatabase.getInstance().getReference("Current Location")
                .setValue(helper).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(task.isSuccessful()){
                    Toast.makeText(DriverMapsActivity.this,"Location Saved",Toast.LENGTH_SHORT);
                }
                else
                {
                    Toast.makeText(DriverMapsActivity.this,"Location not saved",Toast.LENGTH_SHORT);
                }
            }


        );

*/
        /*String userID= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference DriverAvailabilityRef= FirebaseDatabase.getInstance().getReference().child("Driver's Available");

        GeoFire geoFire = new GeoFire(DriverAvailabilityRef);
        geoFire.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {

            }
        });
        */














    }
    protected synchronized void buildGoogleApiClient()
    {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }

   /* @Override
    protected void onStop()
    {
        super.onStop();

        if(!currentLogoutCustomerStatus)
        {
            DisconnectTheDriver();
        }



    }

    private void DisconnectTheDriver()
    {
        String userID= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference DriverAvailabilityRef= FirebaseDatabase.getInstance().getReference().child("Driver's Available").child(userID);
        GeoFire geoFire = new GeoFire(DriverAvailabilityRef);
        DriverAvailabilityRef.removeValue();
    }

    */

    private void LogoutCustomer()
    {
        Intent welcomeIntent = new Intent(CustomerMapsActivity.this,WelcomeActivity.class);
        //welcomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(welcomeIntent);
        finish();
    }

    private void getAssignedDriverInformation()
    {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverFoundID);

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot)
            {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0)
                {
                    String name = dataSnapshot.child("name").getValue().toString();
                    String phone = dataSnapshot.child("phone").getValue().toString();
                    txtName.setText(name);
                    txtPhone.setText(phone);

                    if(dataSnapshot.hasChild("image"))
                    {
                        String image = dataSnapshot.child("image").getValue().toString();
                        Picasso.get().load(image).into(profilePic);

                    }

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }




}

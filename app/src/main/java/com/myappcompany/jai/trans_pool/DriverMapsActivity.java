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
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApi;
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

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;


public class DriverMapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener
        {

    Location currentLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    private static final int REQUEST_CODE = 101;
    private GoogleMap mMap;
    Marker CustomerMarker;
    GoogleApiClient googleApiClient;
    Location lastLocation;
    LocationRequest locationRequest;
    private Button LogoutDriverButton;
    private Button SettingsDriverButton;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private Boolean currentLogoutDriverStatus = false;
    private DatabaseReference AssignedCustomerRef,AssignedCustomerPickupRef;
    private String driverID,customerID="",a;






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_maps);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        driverID = mAuth.getCurrentUser().getUid();


        LogoutDriverButton = (Button) findViewById(R.id.customer_logout_btn);
        SettingsDriverButton = (Button) findViewById(R.id.customer_settings_btn);


       fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLastLocation();

        SettingsDriverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(DriverMapsActivity.this,SettingsActivity.class);
                intent.putExtra("type","Drivers");
                startActivity(intent);

            }
        });



        LogoutDriverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                currentLogoutDriverStatus = true;
                final String userID= FirebaseAuth.getInstance().getCurrentUser().getUid();

                final DatabaseReference sample = FirebaseDatabase.getInstance().getReference().child("Drivers load");
                sample.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if(dataSnapshot.hasChild(userID))
                        {
                            sample.child(userID).removeValue();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });



                final DatabaseReference checkworking = FirebaseDatabase.getInstance().getReference();
                checkworking.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if(dataSnapshot.child("Drivers Working").hasChild(driverID))
                        {
                            checkworking.child(driverID).removeValue();



                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });

                DisconnectTheDriver();
                mAuth.signOut();
                LogoutDriver();
            }
        });
       GetAssignedCustomerRequest(mMap);


    }

            private void GetAssignedCustomerRequest(final GoogleMap googleMap)
            {
                AssignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users")
                        .child("Drivers").child(driverID).child("CustomerRideID");

                AssignedCustomerRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                    {
                        if(dataSnapshot.exists())
                        {
                            customerID = dataSnapshot.getValue().toString();
                            GetAssignedCustomerPickupLocation(googleMap);

                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });

            }

            private void GetAssignedCustomerPickupLocation(GoogleMap googleMap)
            {
                AssignedCustomerPickupRef = FirebaseDatabase.getInstance().getReference().child("Customer Requests")
                        .child(customerID).child("l");

                AssignedCustomerPickupRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                    {
                        if(dataSnapshot.exists())
                        {
                            List<Object> customerLocationMap = (List<Object>) dataSnapshot.getValue();
                            double LocationLat = 0;
                            double LocationLng = 0;

                            if(customerLocationMap.get(0)!= null)
                            {
                                LocationLat = Double.parseDouble(customerLocationMap.get(0).toString());


                            }
                            if(customerLocationMap.get(1)!= null)
                            {
                                LocationLng = Double.parseDouble(customerLocationMap.get(1).toString());


                            }
                            LatLng CustomerLatLng = new LatLng(LocationLat,LocationLng);
                            mMap.addMarker(new MarkerOptions().position(CustomerLatLng).title("Pickup Location"));

                            //CustomerMarker = mMap.addMarker(new MarkerOptions().position(CustomerLatLng).title("Your Driver is Here..."));
                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });

            }


            private void fetchLastLocation()
            {
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

                    supportMapFragment.getMapAsync(DriverMapsActivity.this);

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
        googleMap.addMarker(new MarkerOptions().position(latLng).title("I'm here"));
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
    public void onLocationChanged(Location location)

    {



            lastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(18));


           final String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

            final DatabaseReference DriverAvailabilityRef = FirebaseDatabase.getInstance().getReference().child("Driver's Available");
            final GeoFire geoFireAvailability = new GeoFire(DriverAvailabilityRef);

            final DatabaseReference DriverWorkingRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");
            final GeoFire geoFireWorking = new GeoFire(DriverWorkingRef);

       /*geoFireAvailability.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {

            }
        });
        geoFireWorking.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {

            }
        });
*/





       /*   switch (customerID) {
                case "":
                    //geoFireWorking.removeLocation(userID);
                    geoFireAvailability.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {

                        }
                    });
                    break;
                default:
                   // geoFireAvailability.removeLocation(userID);

                    geoFireWorking.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {

                        }
                    });
                    break;

            }*/

       if(customerID.equals(""))
       {
           DatabaseReference checkworkingref = FirebaseDatabase.getInstance().getReference();
           checkworkingref.addListenerForSingleValueEvent(new ValueEventListener() {
               @Override
               public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                   if(dataSnapshot.child("Drivers Working").hasChild(driverID))
                   {
                       DriverWorkingRef.child(driverID).removeValue();
                       //geoFireWorking.removeLocation(userID);


                   }
               }

               @Override
               public void onCancelled(@NonNull DatabaseError databaseError) {

               }
           });
           geoFireAvailability.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
               @Override
               public void onComplete(String key, DatabaseError error) {

               }
           });
       }
       else
       {
           DatabaseReference checkavailableref=FirebaseDatabase.getInstance().getReference();
           checkavailableref.addListenerForSingleValueEvent(new ValueEventListener() {
               @Override
               public void onDataChange(@NonNull DataSnapshot dataSnapshot)
               {
                   if(dataSnapshot.child("Driver's Available").hasChild(driverID))
                   {
                       String s = dataSnapshot.child("Drivers load").child(driverID).child("Remaining Load Capacity").getValue().toString();
                       int f = Integer.parseInt(s);
                       if(f<=10) {
                           DriverAvailabilityRef.child(driverID).removeValue();
                           //geoFireAvailability.removeLocation(userID);
                       }
                   }

               }

               @Override
               public void onCancelled(@NonNull DatabaseError databaseError) {

               }
           });

           geoFireWorking.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
               @Override
               public void onComplete(String key, DatabaseError error) {

               }
           });
       }










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


            @Override
            protected void onStop()
            {
                super.onStop();

                if(!currentLogoutDriverStatus)
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

            private void LogoutDriver()
            {

                Intent welcomeIntent = new Intent(DriverMapsActivity.this,WelcomeActivity.class);
                //welcomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(welcomeIntent);
                finish();
            }






        }
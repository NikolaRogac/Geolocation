package link.linkcompass;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    TextView azimuthTextView;
    ImageView compassImageView;

    Button getLocation;
    TextView latLng;
    TextView tvDeclination;

    public float currDeclination;

    private static final int LOCATION_PERMISSION = 1;

    private SensorManager sensorManager;

    private Sensor magnetometerSensor;
    private Sensor accelerometerSensor;
    private Sensor gravitySensor;
    private Sensor rotationVectorSensor;

    int azimuth;
    int oldAzimuth = 0;


    Window window;
    WindowManager.LayoutParams layoutParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        azimuthTextView = findViewById(R.id.azimuthTextView);
        compassImageView = findViewById(R.id.compassFrontImageView);

        getLocation = findViewById(R.id.btnGetLoc);
        latLng = findViewById(R.id.tvLatLng);
        tvDeclination = findViewById(R.id.tvDeclination);

        getLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION);
                } else {
                    getCurrentLocation();
                }
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);


        //keep screen on
        window = getWindow();
        layoutParams = window.getAttributes();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onRequestPermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getCurrentLocation() {

        final LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.getFusedLocationProviderClient(MainActivity.this)
                .requestLocationUpdates(locationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        super.onLocationResult(locationResult);
                        LocationServices.getFusedLocationProviderClient(MainActivity.this)
                                .removeLocationUpdates(this);
                        if (locationResult != null && locationResult.getLocations().size() > 0)
                        {
                            int latestLocationIndex = locationResult.getLocations().size() - 1;
                            double latitude = locationResult.getLocations().get(latestLocationIndex).getLatitude();
                            double longitude = locationResult.getLocations().get(latestLocationIndex).getLongitude();
                            double altitude = locationResult.getLocations().get(latestLocationIndex).getAltitude();
                            latLng.setText(String.format("Latitude: %s\nLongitude: %s\nAltitude: %s", latitude, longitude,altitude));

                            GeomagneticField geoField = new GeomagneticField((float) latitude,
                                    (float)longitude,
                                    (float)altitude,
                                    System.currentTimeMillis());

                            currDeclination=geoField.getDeclination();
                            tvDeclination.setText(String.format("Declination:"+currDeclination));
                        }
                    }
                }, Looper.getMainLooper());
    }

    int operationMode;

    @Override
    protected void onResume() {
        super.onResume();

        if (rotationVectorSensor != null) {

            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);

            operationMode = 2;

        } else if (gravitySensor != null && magnetometerSensor != null) {

            sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);

            operationMode = 1;

        } else if (magnetometerSensor != null && accelerometerSensor != null) {

            sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

            operationMode = 0;

        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }


    float[] magneticField = new float[3];
    float[] acceleration = new float[3];
    float[] gravity = new float[3];

    float[] rotationMatrix = new float[9];
    float[] inclinationMatrix = new float[9];
    float[] orientation = new float[3];

    float[] rotationVector = new float[5];


    ObjectAnimator rotationAnimation;

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(sensorEvent.values, 0, magneticField, 0, sensorEvent.values.length);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(sensorEvent.values, 0, acceleration, 0, sensorEvent.values.length);
                break;
            case Sensor.TYPE_GRAVITY:
                System.arraycopy(sensorEvent.values, 0, gravity, 0, sensorEvent.values.length);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                System.arraycopy(sensorEvent.values, 0, rotationVector, 0, sensorEvent.values.length);
                break;
        }

        if (operationMode == 0 || operationMode == 1) {

            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, (operationMode == 0) ? acceleration : gravity, magneticField)) {

                float azimuthRad = SensorManager.getOrientation(rotationMatrix, orientation)[0]; //azimuth in radians
                double azimuthDeg = Math.toDegrees(azimuthRad); // azimuth in degrees; value from -180 to 180

                azimuth = ((int) azimuthDeg + 360) % 360; //convert -180/180 to 0/360
                azimuth = (int) (azimuth - currDeclination);


            }

        } else {

            // calculate the rotation matrix
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);
            // get the azimuth value (orientation[0]) in degree
            azimuth = (int) (Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientation)[0]) + 360) % 360;
            azimuth = (int) (azimuth - currDeclination);

        }

        azimuthTextView.setText(String.valueOf(azimuth) + "°");
        //compassImageView.setRotation(-azimuth); //set orientation without animation

        //set rotation with animation

        float tempAzimuth;
        float tempCurrentAzimuth;

        if (Math.abs(azimuth - oldAzimuth) > 180) {
            if (oldAzimuth < azimuth) {
                tempCurrentAzimuth = oldAzimuth + 360;
                tempAzimuth = azimuth;
            } else {
                tempCurrentAzimuth = oldAzimuth;
                tempAzimuth = azimuth + 360;
            }
            rotationAnimation = ObjectAnimator.ofFloat(compassImageView, "rotation", -tempCurrentAzimuth, -tempAzimuth);
            rotationAnimation.setDuration(250);
            rotationAnimation.start();
        } else {
            rotationAnimation = ObjectAnimator.ofFloat(compassImageView, "rotation", -oldAzimuth, -azimuth);
            rotationAnimation.setDuration(250);
            rotationAnimation.start();
        }
        oldAzimuth = azimuth;


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /**************ACTION BAR MENU************************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.help:

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setTitle("Compass");
                alertDialogBuilder.setMessage("This is Compass app, part of the ITAcademy and LinkAcademy Android Development Program.\n\nAuthor: Vladimir Dresevic, Link Group");

                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }
}

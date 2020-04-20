package com.adafruit.bluefruit.le.connect.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.DashPathEffect;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;
import com.adafruit.bluefruit.le.connect.ble.central.UartDataManager;
import com.adafruit.bluefruit.le.connect.style.UartStyle;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vokaturi.vokaturisdk.entities.Voice;
import vokaturi.vokaturisdk.entities.EmotionProbabilities;


public class AcesoFragment extends ConnectedPeripheralFragment implements UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = AcesoFragment.class.getSimpleName();

    // Data
    private UartDataManager mUartDataManager;
    private List<BlePeripheralUart> mBlePeripheralsUart = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // region Fragment Lifecycle
    public static AcesoFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        AcesoFragment fragment = new AcesoFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public AcesoFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes
        setRetainInstance(true);


//        VOKATURI STUFF HERE
//        Might be helpful? https://github.com/alshell7/VokaturiAndroid

//        We need to feed it the sample rate and such

        MediaExtractor mex = new MediaExtractor();
        try {
            mex.setDataSource(getResources().openRawResourceFd(R.raw.hello));
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat mf = mex.getTrackFormat(0);

        int bitRate = mf.getInteger(MediaFormat.KEY_BIT_RATE);
        int sampleRate = mf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//        recording.fill();
//        logVokMetrics(recording.extract());
    }


    private void logVokMetrics(EmotionProbabilities emotionProbabilities)
    {
        Log.i("Neutrality: ", String.valueOf(emotionProbabilities.neutrality));
        Log.i("Happiness: ", String.valueOf(emotionProbabilities.happiness));
        Log.i("Sadness: ", String.valueOf(emotionProbabilities.sadness));
        Log.i("Anger: ", String.valueOf(emotionProbabilities.anger));
        Log.i("Fear: ", String.valueOf(emotionProbabilities.fear));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_plotter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.aceso_tab_title);

        // Setup
        Context context = getContext();
        if (null != context) {
            mUartDataManager = new UartDataManager(context, this, true);
            setupUart();
        }
    }

    @Override
    public void onDestroy() {
        if (null != mUartDataManager) {
            Context context = getContext();
            if (context != null) mUartDataManager.setEnabled(context, false);
        }

        if (null != mBlePeripheralsUart) {
            for (BlePeripheralUart blePeripheralUart : mBlePeripheralsUart)
                blePeripheralUart.uartDisable();
            mBlePeripheralsUart.clear();
            mBlePeripheralsUart = null;
        }
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        if (R.id.action_help == item.getItemId()) {
            if (null != activity) {
                inputStream = getResources().openRawResource(R.raw.happy_sad_new);
                CheckLogin checkLogin = new CheckLogin();
                checkLogin.execute("");
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    // endregion


    // region Uart

    private boolean isInMultiUartMode() {
        return mBlePeripheral == null;
    }

    private void setupUart() {
        // Line dashes assigned to peripherals
        final DashPathEffect[] dashPathEffects = UartStyle.defaultDashPathEffects();

        // Enable uart
        if (isInMultiUartMode()) {
            List<BlePeripheral> connectedPeripherals = BleScanner.getInstance().getConnectedPeripherals();
            for (int i = 0; i < connectedPeripherals.size(); i++) {
                BlePeripheral blePeripheral = connectedPeripherals.get(i);

                if (!BlePeripheralUart.isUartInitialized(blePeripheral, mBlePeripheralsUart)) {
                    BlePeripheralUart blePeripheralUart = new BlePeripheralUart(blePeripheral);
                    mBlePeripheralsUart.add(blePeripheralUart);
                    blePeripheralUart.uartEnable(mUartDataManager, status -> {

                        String peripheralName = blePeripheral.getName();
                        if (null == peripheralName) peripheralName = blePeripheral.getIdentifier();

                        String finalPeripheralName = peripheralName;
                        mMainHandler.post(() -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                // Done
                                Log.d(TAG, "Uart enabled for: " + finalPeripheralName);
                            } else {
                                //WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                AlertDialog dialog = builder.setMessage(String.format(getString(R.string.uart_error_multipleperiperipheralinit_format), finalPeripheralName))
                                        .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {})
                                        .show();
                                DialogUtils.keepDialogOnOrientationChanges(dialog);
                            }
                        });
                    });
                }
            }

        } else {       //  Single peripheral mode
            if (!BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
                BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
                mBlePeripheralsUart.add(blePeripheralUart);
                blePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
                    // Done
                    if (BluetoothGatt.GATT_SUCCESS == status) Log.d(TAG, "Uart enabled");
                    else {
                        Context context = getContext();
                        if (null != context) {
                            WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                                    .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null)
                                            strongBlePeripheralUart.disconnect();
                                    })
                                    .show();
                            DialogUtils.keepDialogOnOrientationChanges(dialog);
                        }
                    }
                }));
            }
        }
    }

//    static String packetsAsCsv(List<UartPacket> packets, boolean isHexFormat) {
//        StringBuilder text = new StringBuilder("Timestamp,Mode,Data\r\n");        // csv Header
//
//        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss:SSS", Locale.US);
//
//        for (UartPacket packet : packets) {
//            Date date = new Date(packet.getTimestamp());
//            String dateString = dateFormat.format(date).replace(",", ".");      //  comma messes with csv, so replace it by a point
//            String mode = packet.getMode() == UartPacket.TRANSFERMODE_RX ? "RX" : "TX";
//            String dataString = isHexFormat ? BleUtils.bytesToHex2(packet.getData()) : BleUtils.bytesToText(packet.getData(), true);
//
//            // Remove newline characters from data (it messes with the csv format and Excel wont recognize it)
//            dataString = dataString.trim();
//            text.append(String.format(Locale.ENGLISH, "%s,%s,%s\r\n", dateString, mode, dataString));
//        }
//
//        return text.toString();
//    }

    public TextView message;
    private InputStream inputStream;

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {

    }

    public class CheckLogin extends AsyncTask<String, String, String>{
        String z = "";
        Boolean isSuccess = false;

        protected void onPreExecute(){
        }
        @Override
        protected String doInBackground(String... params){
            try{
                Connection con = connectionclass();
                if(con == null) z = "Check your internet access!";
                else{
                    String sql = "INSERT INTO dbo.Prototype(UserID,DateTime,Emotion,GSR,BPM)\n" +
                            "VALUES (?,?,?,?,?)";
                    try {
                        BufferedReader bReader = new BufferedReader(new InputStreamReader(inputStream));
                        String line;
                        while ((line = bReader.readLine() ) != null) {
                            try {

                                if (null != line)
                                {
                                    String[] array = line.split(",+");
                                        //Create preparedStatement here and set them and excute them
                                        PreparedStatement ps = con.prepareStatement(sql);
                                        ps.setInt(1,Integer.parseInt(array[0].trim()));
                                        ps.setInt(2,Integer.parseInt(array[1].trim()));
                                        ps.setString(3,array[2].trim());
                                        ps.setFloat(4,Float.parseFloat(array[3].trim()));
                                        ps.setFloat(5,Float.parseFloat(array[4].trim()));
                                        ps.executeUpdate();
                                        ps.close();
                                        Log.d("sql", "FINISHED");

                                }
                            } catch (SQLException e) {
                                Log.d("sql", "ERROR");
                                e.printStackTrace();
                            } finally
                            {
                                Log.d("sql", "closed");
                                if (null == bReader)
                                    bReader.close();
                            }
                            //line= bReader.readLine();
                        }
                    } catch (FileNotFoundException ex) {
                        ex.printStackTrace();
                }catch (Exception ex){
                isSuccess=false;
                z=ex.getMessage();
                Log.d("sql error", z);
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
    return z;
        }
    }


    @SuppressLint({"NewApi", "AuthLeak"})
    private Connection connectionclass(){
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Connection connection = null;
        String ConnectionURL;
        try{
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            ConnectionURL = "jdbc:jtds:sqlserver://aceso-test-1.database.windows.net:1433;DatabaseName=aceso-test;user=aceso-test@aceso-test-1;password=UTAustin2020;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";
            connection = DriverManager.getConnection(ConnectionURL);
        }
        catch(SQLException se){
            Log.e("error here 1: ", Objects.requireNonNull(se.getMessage()));
        }
        catch(ClassNotFoundException e){
            Log.e("error here 2: ", Objects.requireNonNull(e.getMessage()));
        }
        catch(Exception e){
            Log.e("error here 3: ", Objects.requireNonNull(e.getMessage()));
        }
        return connection;
    }
}


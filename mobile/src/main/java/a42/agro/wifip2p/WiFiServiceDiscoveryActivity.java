package a42.agro.wifip2p;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import a42.agro.wifip2p.WiFiDirectServicesList.DeviceClickListener;
import a42.agro.wifip2p.WiFiDirectServicesList.WiFiDevicesAdapter;
import a42.agro.wifip2p.WiFiChatFragment.MessageTarget;


public class WiFiServiceDiscoveryActivity extends Activity implements DeviceClickListener, Handler.Callback, MessageTarget,
        WifiP2pManager.ConnectionInfoListener {

    public static final String TAG = "wifidirectdemo";

    // TXT RECORD properties
    public static final String TXTRECORD_PROB_AVAILABLE = "evailable";
    public static final String SERVICE_INSTANCE = "_wifidemotest";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;
    private WifiP2pManager manager;

    static final int SERVER_PORT = 4545;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    private WifiP2pDnsSdServiceRequest serviceRequest;

    private Handler handler = new Handler(this);
    private WiFiChatFragment chatFragment;
    private WiFiDirectServicesList servicesList;


    private TextView statusTxtView;

    public Handler getHandler() {
        return handler;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        statusTxtView = (TextView) findViewById(R.id.status_text);

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        startRegistrationAndDiscovery();

        servicesList = new WiFiDirectServicesList();

        getFragmentManager().beginTransaction().add(R.id.container_root, servicesList, "services").commit();


    }


    @Override
    protected void onRestart() {
        Fragment frag = getFragmentManager().findFragmentByTag("services");
        if (frag != null) {
            getFragmentManager().beginTransaction().remove(frag).commit();
        }
        super.onRestart();
    }

    @Override
    protected void onStop() {
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new ActionListener() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                }
            });
        }
        super.onStop();
    }


    /**
     * Registers a local service and then initiates a service discovery
     */

    private void startRegistrationAndDiscovery() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROB_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Added Local Service");

            }

            @Override
            public void onFailure(int reasonCode) {
                appendStatus("Failed to add a service");
            }
        });
        discoverService();
    }

    private void discoverService() {
        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */

        manager.setDnsSdResponseListeners(channel,
                new DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {

                        // A service has been discovered. Is this our app?

                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                            // update the UI and add the item the discovered
                            // device.
                            WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
                                    .findFragmentByTag("services");
                            if (fragment != null) {
                                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
                                        .getListAdapter());
                                WiFiP2pService service = new WiFiP2pService();
                                service.device = srcDevice;
                                service.instanceName = instanceName;
                                service.serviceRegistrationType = registrationType;
                                adapter.add(service);
                                adapter.notifyDataSetChanged();
                                Log.d(TAG, "onBonjourServiceAvailable "
                                        + instanceName);
                            }
                        }

                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {

                    /**
                     * A new TXT record is available. Pick up the advertised
                     * buddy name.
                     */

                    @Override
                    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> record,
                                                          WifiP2pDevice device) {
                        Log.d(TAG, device.deviceName + "is"
                                + record.get(TXTRECORD_PROB_AVAILABLE));

                    }
                });

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new ActionListener() {
                    @Override
                    public void onSuccess() {

                        appendStatus("Added service discovery request");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        appendStatus("Failed adding service discovery request");
                    }
                });

        manager.discoverServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                appendStatus("Service discovery failed");

            }
        });

    }


    @Override
    public void connectP2p(WiFiP2pService service) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        if (serviceRequest != null)
            manager.removeServiceRequest(channel, serviceRequest, new ActionListener() {
                @Override
                public void onSuccess() {
                    appendStatus("Service remove");
                }

                @Override
                public void onFailure(int arg0) {
                    appendStatus("Service removido failed");
                }
            });
        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Connect to service");
            }

            @Override
            public void onFailure(int arg0) {
                appendStatus("Failed connecting to service");

            }
        });

    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                Log.d(TAG, readMessage);
                (chatFragment).pushMessage("Buddy: " + readMessage);
                break;
            case MY_HANDLE:
                Object obj = msg.obj;
                (chatFragment).setChatManager((ChatManager) obj);
        }
        return true;
    }


    @Override
    protected void onResume() {
        super.onResume();
        receiver =
    }

    public void appendStatus(String status) {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {

        Thread handler = null;
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */

        if (p2pInfo.isGroupOwner) {
            Log.d(TAG, "Connected as group owner");
            try {
                handler = new GroupOwnerSocketHandler(
                        ((MessageTarget) this).getHandler());
                handler.start();
            } catch (IOException e) {
                Log.d(TAG,
                        "Failed to create a server thread - " + e.getMessage());
                return;
            }
        }


    }
}

package net.yupol.transmissionremote.app.transport;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Strings;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import net.yupol.transmissionremote.app.server.Server;
import net.yupol.transmissionremote.app.transport.request.Request;

import org.apache.http.HttpStatus;

import java.util.Timer;
import java.util.TimerTask;

public class SpiceTransportManager extends SpiceManager implements TransportManager {

    private static final String TAG = SpiceTransportManager.class.getSimpleName();

    private Timer timer;
    private String sessionId;
    private Server server;

    public SpiceTransportManager() {
        super(NoCacheGoogleHttpClientSpiceService.class);
    }

    public synchronized void setServer(Server server) {
        this.server = server;
        if (server != null) {
            this.sessionId = server.getLastSessionId();
        }
    }

    private synchronized void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public <T> void doRequest(final Request<T> request, final RequestListener<T> listener) {

        setupRequest(request);

        execute(request, new PropagateRequestListener<T>(listener) {
            @Override
            protected boolean onFailure(SpiceException spiceException) {
                if (request.getResponseStatusCode() == HttpStatus.SC_CONFLICT) {
                    setSessionId(request.getResponseSessionId());
                    server.setLastSessionId(sessionId);
                    Log.d(TAG, "new sessionId: " + sessionId);
                    doRequest(request, listener);
                    return false;
                }
                return true;
            }

            @Override
            protected boolean onSuccess(T t) {
                return true;
            }
        });
    }

    @Override
    public <T> void doRequest(final Request<T> request, final RequestListener<T> listener, long delay) {
        if (timer == null) throw new IllegalStateException("doRequest called while SpiceTransportManager is stopped");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (SpiceTransportManager.this) {
                    if (timer != null) {
                        doRequest(request, listener);
                    }
                }
            }
        }, delay);
    }

    @Override
    public synchronized void start(Context context) {
        super.start(context);
        timer = new Timer("SpiceTransportManger timer");
    }

    @Override
    public synchronized void shouldStop() {
        synchronized (this) {
            timer.cancel();
            timer = null;
            super.shouldStop();
        }
    }

    private synchronized void setupRequest(Request<?> request) {
        if (server == null)
            throw new IllegalStateException("Trying to send request while there is no active server");
        request.setServer(server);
        request.setSessionId(Strings.nullToEmpty(sessionId));
    }
}

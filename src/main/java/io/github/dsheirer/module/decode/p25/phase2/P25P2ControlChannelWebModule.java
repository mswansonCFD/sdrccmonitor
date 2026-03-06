/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.module.decode.p25.phase2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.IMessageListener;
import io.github.dsheirer.module.Module;
import io.github.dsheirer.module.decode.p25.phase2.message.mac.MacMessage;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.util.TimeStamp;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web module that publishes recent Phase II control channel MAC messages.
 */
public class P25P2ControlChannelWebModule extends Module implements IMessageListener, Listener<IMessage>
{
    private static final Logger mLog = LoggerFactory.getLogger(P25P2ControlChannelWebModule.class);
    private static final String ENABLED_PROPERTY = "sdrtrunk.p25p2.web.enabled";
    private static final String PORT_PROPERTY = "sdrtrunk.p25p2.web.port";
    private static final int DEFAULT_PORT = 9077;
    private static final int DEFAULT_HISTORY_SIZE = 300;

    private static final P25P2ControlChannelWebService WEB_SERVICE = new P25P2ControlChannelWebService();

    private final String mChannelName;

    public P25P2ControlChannelWebModule(String channelName)
    {
        mChannelName = channelName;
    }

    @Override
    public Listener<IMessage> getMessageListener()
    {
        return this;
    }

    @Override
    public void reset()
    {
    }

    @Override
    public void start()
    {
        if(Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true")))
        {
            WEB_SERVICE.start();
        }
    }

    @Override
    public void stop()
    {
    }

    @Override
    public void receive(IMessage message)
    {
        if(message instanceof MacMessage macMessage && macMessage.getDataUnitID().isLCCH())
        {
            WEB_SERVICE.record(mChannelName, macMessage);
        }
    }

    private static class P25P2ControlChannelWebService
    {
        private final Gson mGson = new GsonBuilder().disableHtmlEscaping().create();
        private final Deque<Map<String, Object>> mRecentMessages = new ArrayDeque<>();
        private HttpServer mHttpServer;

        public synchronized void start()
        {
            if(mHttpServer != null)
            {
                return;
            }

            int port = Integer.getInteger(PORT_PROPERTY, DEFAULT_PORT);

            try
            {
                mHttpServer = HttpServer.create(new InetSocketAddress(port), 0);
                mHttpServer.createContext("/api/p25/phase2/control-channel", new MessagesHandler());
                mHttpServer.start();
                mLog.info("Started P25 Phase II control channel web service at http://0.0.0.0:{}{}", port,
                    "/api/p25/phase2/control-channel");
            }
            catch(IOException ioe)
            {
                mLog.error("Unable to start P25 Phase II control channel web service on port {}", port, ioe);
            }
        }

        public synchronized void record(String channelName, MacMessage macMessage)
        {
            Map<String, Object> record = new HashMap<>();
            record.put("timestamp", macMessage.getTimestamp());
            record.put("time", TimeStamp.getTimeStamp(macMessage.getTimestamp()));
            record.put("channel", channelName);
            record.put("timeslot", macMessage.getTimeslot());
            record.put("duid", macMessage.getDataUnitID().toString());
            record.put("valid", macMessage.isValid());
            record.put("bitErrors", macMessage.getBitErrorCount());
            record.put("nac", macMessage.hasNAC() ? macMessage.getNAC().toString() : null);
            record.put("pduType", macMessage.getMacPduType().toString());
            record.put("payload", macMessage.getMacStructure().toString());
            record.put("message", macMessage.toString());
            mRecentMessages.addFirst(record);

            while(mRecentMessages.size() > DEFAULT_HISTORY_SIZE)
            {
                mRecentMessages.removeLast();
            }
        }

        private class MessagesHandler implements HttpHandler
        {
            @Override
            public void handle(HttpExchange exchange) throws IOException
            {
                if(!"GET".equals(exchange.getRequestMethod()))
                {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                byte[] payload;

                synchronized(P25P2ControlChannelWebService.this)
                {
                    List<Map<String, Object>> messageList = new ArrayList<>(mRecentMessages);
                    payload = mGson.toJson(messageList).getBytes(StandardCharsets.UTF_8);
                }

                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, payload.length);

                try(OutputStream outputStream = exchange.getResponseBody())
                {
                    outputStream.write(payload);
                }
            }
        }
    }
}

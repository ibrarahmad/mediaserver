/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.media.control.mgcp.connection;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.mobicents.media.control.mgcp.exception.MgcpConnectionException;
import org.mobicents.media.control.mgcp.listener.MgcpConnectionListener;
import org.mobicents.media.control.mgcp.message.LocalConnectionOptions;
import org.mobicents.media.server.impl.rtp.channels.AudioChannel;
import org.mobicents.media.server.impl.rtp.channels.MediaChannelProvider;
import org.mobicents.media.server.io.sdp.format.AVProfile;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class MgcpRemoteConnectionTest {

    private ListeningScheduledExecutorService executor;

    @Before
    public void before() {
        this.executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1));
    }

    public void after() {
        this.executor.shutdownNow();
        this.executor = null;
    }

    @Test
    public void testMaxDurationTimerWhenHalfOpen() throws MgcpConnectionException, InterruptedException {
        // given
        final int identifier = 1;
        final int halfOpenTimeout = 2;
        final int openTimeout = 3;
        final MgcpConnectionListener listener = mock(MgcpConnectionListener.class);
        final AudioChannel audioChannel = mock(AudioChannel.class);
        final MediaChannelProvider channelProvider = mock(MediaChannelProvider.class);

        // when
        when(channelProvider.provideAudioChannel()).thenReturn(audioChannel);
        when(audioChannel.getFormats()).thenReturn(AVProfile.audio);
        when(audioChannel.getMediaType()).thenReturn(AudioChannel.MEDIA_TYPE);

        final MgcpRemoteConnection connection = new MgcpRemoteConnection(identifier, halfOpenTimeout, openTimeout, channelProvider, this.executor);
        connection.setConnectionListener(listener);
        connection.halfOpen(new LocalConnectionOptions());
        Thread.sleep(halfOpenTimeout * 1000 + 200);

        // then
        assertEquals(MgcpConnectionState.CLOSED, connection.state);
        verify(listener, only()).onConnectionFailure(connection);
    }

    @Test
    public void testMaxDurationTimerWhenOpen() throws MgcpConnectionException, InterruptedException {
        // given
        final int openTimeout = 4;
        final int halfOpenTimeout = openTimeout / 2;
        final MgcpConnectionListener listener = mock(MgcpConnectionListener.class);
        final AudioChannel audioChannel = mock(AudioChannel.class);
        final MediaChannelProvider channelProvider = mock(MediaChannelProvider.class);

        // when
        when(channelProvider.provideAudioChannel()).thenReturn(audioChannel);
        when(audioChannel.getFormats()).thenReturn(AVProfile.audio);
        when(audioChannel.getMediaType()).thenReturn(AudioChannel.MEDIA_TYPE);
        when(audioChannel.containsNegotiatedFormats()).thenReturn(true);

        final MgcpRemoteConnection connection1 = new MgcpRemoteConnection(1, halfOpenTimeout, openTimeout, channelProvider, this.executor);
        connection1.setConnectionListener(listener);
        final String sdp1 = connection1.halfOpen(new LocalConnectionOptions());

        final MgcpRemoteConnection connection2 = new MgcpRemoteConnection(2, halfOpenTimeout, openTimeout, channelProvider, this.executor);
        connection2.setConnectionListener(listener);
        connection2.open(sdp1);

        // then
        Thread.sleep(halfOpenTimeout * 1000 + 200);
        assertEquals(MgcpConnectionState.CLOSED, connection1.state);
        verify(listener, times(1)).onConnectionFailure(connection1);
        assertEquals(MgcpConnectionState.OPEN, connection2.state);

        Thread.sleep(halfOpenTimeout * 1000 + 200);
        assertEquals(MgcpConnectionState.CLOSED, connection2.state);
        verify(listener, times(1)).onConnectionFailure(connection2);
    }

    @Test
    public void testHalfOpenTimerCancelationWhenMovingToOpen() throws MgcpConnectionException, InterruptedException {
        // given
        final int openTimeout = 4;
        final int halfOpenTimeout = openTimeout / 2;
        final MgcpConnectionListener listener = mock(MgcpConnectionListener.class);
        final AudioChannel audioChannel = mock(AudioChannel.class);
        final MediaChannelProvider channelProvider = mock(MediaChannelProvider.class);

        // when
        when(channelProvider.provideAudioChannel()).thenReturn(audioChannel);
        when(audioChannel.getFormats()).thenReturn(AVProfile.audio);
        when(audioChannel.getMediaType()).thenReturn(AudioChannel.MEDIA_TYPE);
        when(audioChannel.containsNegotiatedFormats()).thenReturn(true);

        final MgcpRemoteConnection connection1 = new MgcpRemoteConnection(1, halfOpenTimeout, openTimeout, channelProvider, this.executor);
        connection1.setConnectionListener(listener);
        final String sdp1 = connection1.halfOpen(new LocalConnectionOptions());

        final MgcpRemoteConnection connection2 = new MgcpRemoteConnection(2, halfOpenTimeout, openTimeout, channelProvider, this.executor);
        connection2.setConnectionListener(listener);
        final String sdp2 = connection2.open(sdp1);

        connection1.open(sdp2);

        // then
        Thread.sleep(halfOpenTimeout * 1000 + 200);
        assertEquals(MgcpConnectionState.OPEN, connection1.state);
        assertEquals(MgcpConnectionState.OPEN, connection2.state);

        Thread.sleep(halfOpenTimeout * 1000 + 200);
        assertEquals(MgcpConnectionState.CLOSED, connection1.state);
        verify(listener, times(1)).onConnectionFailure(connection1);
        assertEquals(MgcpConnectionState.CLOSED, connection2.state);
        verify(listener, times(1)).onConnectionFailure(connection2);
    }

}

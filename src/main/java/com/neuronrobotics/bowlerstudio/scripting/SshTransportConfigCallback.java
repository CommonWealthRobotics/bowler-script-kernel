package com.neuronrobotics.bowlerstudio.scripting;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

public class SshTransportConfigCallback implements TransportConfigCallback {
    org.eclipse.jgit.transport.sshd.SshdSessionFactory sshSessionFactory = new org.eclipse.jgit.transport.sshd.SshdSessionFactory();

    @Override
    public void configure(Transport transport) {
        SshTransport sshTransport = (SshTransport) transport;
		sshTransport.setSshSessionFactory(sshSessionFactory);
    }
}

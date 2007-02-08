/*
	Copyright Jim Brain and Brain Innovations, 2005.

	This file is part of QLinkServer.

	QLinkServer is free software; you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation; either version 2 of the License, or
	(at your option) any later version.

	QLinkServer is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with QLinkServer; if not, write to the Free Software
	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

	@author Jim Brain
	Created on Jul 18, 2005
	
 */
package org.jbrain.qlink;

import java.io.*;
import java.net.*;
import java.util.*;


import org.apache.log4j.Logger;
import org.jbrain.qlink.connection.QConnection;



public class QTCPListener {
	private static Logger _log=Logger.getLogger(QTCPListener.class);
	private static int _iPort=5190;
	
	class ProxyThread extends Thread {
		Socket _socket;
		public ProxyThread(Socket s) {
			_socket=s;
		}
		
		public void run() {
			InputStream is;
			OutputStream os;
			QConnection conn;
			QServer server;
			
			try {
				is = _socket.getInputStream();
				os = _socket.getOutputStream();
				TelenetProxy proxy=new TelenetProxy(is,os);
				if(proxy.negotiate()) {
					conn=new QConnection(is,os);
					// we got through the Telenet cmds, now switch to QLink protocol.
					server=new QServer(conn);
					//not sure if we should track open servers or not.
					//servers.add(server);
				}
			} catch (IOException e) {
				_log.error(e);
			}
			
		}
	}
	
	public int run() {
		int rc=0;
		ArrayList servers=new ArrayList();
		QConnection conn;
		QServer server;
 		ServerSocket serverSocket = null; 
		Socket clientSocket = null; 
		
		_log.info("Starting server");
	    try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (Exception e) {
			_log.fatal("Could not load MySQL JDBC driver",e);
			rc=-1;
		}
        
		if(rc==0) {
			try { 
				serverSocket = new ServerSocket(_iPort); 
			} catch (IOException e) { 
				_log.fatal("Could not listen on port " + _iPort,e);
				rc=-1;
			}
		}
		if(rc==0) {
			try {
				while(true) {
					clientSocket = serverSocket.accept();
					_log.info("Incoming connection received");
					new ProxyThread(clientSocket).start();
				}
			} catch (IOException e) { 
				_log.fatal("TCP/IP accept failed.",e);
				rc=-1;
			}
		}
 		_log.info("Terminating server");
 		return rc;
		
	}

	public static void main(String[] args) {
		if(args.length>0) {
			try {
				_iPort=Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				_log.fatal("Invalid port number '" + args[0] + "'");
				System.exit(-1);	
			}
		}
			
		System.exit(new QTCPListener().run());
	}
}

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
	Created on Jul 25, 2005
	
 */
package org.jbrain.qlink.chat;

import java.util.EventObject;

public class GameEvent extends EventObject implements RoomEvent {
	private int _iSeat;
	private int _iType;
	private String _sHandle;
	
	public static final int READY_TO_START=1;
	public static final int REQUEST_RESTART=2;
	public static final int ACCEPT_INVITE=3;
	public static final int ACCEPT_RESTART=4;
	public static final int DECLINE_INVITE=5;
	public static final int DECLINE_RESTART=6;
	public static final int REQUEST_LOAD = 7;
	public static final int LEAVE_GAME = 8;
	public static final int RESTART_GAME = 10;
	public static final int START_GAME = 11;

	public GameEvent(Object obj,int type, int seat, String handle) {
		super(obj);
		_iSeat=seat;
		_iType=type;
		_sHandle=handle;
	}
	
	public int getSeat() {
		return _iSeat;
	}
	
	public int getType() {
		return _iType;
	}
	
	public String getHandle() {
		return _sHandle;
	}
	
}

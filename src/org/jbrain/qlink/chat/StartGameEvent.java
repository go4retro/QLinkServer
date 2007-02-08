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

public class StartGameEvent extends EventObject implements RoomEvent {
	private int _iSeat;
	private String _sHandle;
	private byte[] _order;

	public StartGameEvent(Object obj,int seat, String handle, byte[] order) {
		super(obj);
		_iSeat=seat;
		_sHandle=handle;
		_order=order;
	}
	
	public int getSeat() {
		return _iSeat;
	}
	
	public byte[] getPlayOrder() {
		return _order;
	}

	/**
	 * @return
	 */
	public String getHandle() {
		return _sHandle;
	}
}

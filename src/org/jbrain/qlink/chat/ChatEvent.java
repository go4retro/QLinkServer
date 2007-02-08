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

public class ChatEvent extends EventObject implements RoomEvent {
	private int _iFromSeat;
	private String _sFromName;
	private boolean _bSeated;
	private String _sText;

	public ChatEvent(Object obj, String name, String text) {
		super(obj);
		_iFromSeat=0;
		_sFromName=name;
		_bSeated=false;
		_sText=text;
	}
	
	public ChatEvent(Object obj,int seat, String text) {
		super(obj);
		_iFromSeat=seat;
		_sFromName="";
		_bSeated=true;
		_sText=text;
	}
	
	public int getOriginatingSeat() {
		return _iFromSeat;
	}
	
	public String getOriginatingName() {
		return _sFromName;
	}
	
	public boolean isSeated() {
		return _bSeated;
	}
	
	public String getText() {
		return _sText;
	}
}

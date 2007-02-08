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
	Created on Jul 27, 2005
	
 */
package org.jbrain.qlink.chat;


public class SeatInfo {
	private String _sName;
	private int _iSeat;
	private boolean _bGamePending;
	
	public SeatInfo(String name, int seat) {
		_sName=name;
		_iSeat=seat;
		_bGamePending=false;
	}

	/**
	 * @return
	 */
	public int getSeat() {
		return _iSeat;
	}
	
	/**
	 * @return
	 */
	public String getHandle() {
		return _sName;
	}
	
	void setGameStatus(boolean b) {
		_bGamePending=b;
	}
	
	/**
	 * @return
	 */
	public boolean isInGame() {
		return _bGamePending;
	}
	
}
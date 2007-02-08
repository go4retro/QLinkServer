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
 Created on Aug 23, 2005
 
 */
package org.jbrain.qlink.chat;

public interface Room {

	/**
	 * @return
	 * 
	 * @uml.property name="name" multiplicity="(0 1)"
	 */
	public abstract String getName();


	/**
	 * @return
	 */
	public abstract int getPopulation();

	/**
	 * @param i
	 * @return
	 */
	public abstract SeatInfo[] getSeatInfoList();

	public abstract void addEventListener(RoomEventListener listener);
	public abstract void removeEventListener(RoomEventListener listener);

	public abstract void say(String[] text);

	public abstract void say(String text);

	public abstract void leave();

	/**
	 * @param name
	 * @param type
	 * @param systemPickOrder
	 * @return
	 */
	public abstract Game createGame(int id, String name, String type, boolean systemPickOrder);

	/**
	 * @return
	 * 
	 * @uml.property name="pendingGame"
	 * @uml.associationEnd multiplicity="(0 1)"
	 */
	public abstract Game getPendingGame();


	/**
	 * @param handle
	 * @return
	 */
	public abstract SeatInfo getSeatInfo(String handle);

	/**
	 * @return
	 */
	public abstract boolean isPublicRoom();
	
	public GameInfo[] getGameInfoList();

	/**
	 * 
	 * @uml.property name="info" multiplicity="(0 1)"
	 */
	public String getInfo();


	/**
	 * @param handle
	 * @return
	 */
	public abstract ObservedGame observeGame(String handle);

}
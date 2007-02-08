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
 Created on Oct 2, 2005
 
 */
package org.jbrain.qlink.chat;

import org.jbrain.qlink.user.QHandle;

public interface QRoom {
	public String getName();
	public int getPopulation();
	QSeat addUser(QHandle handle, ChatProfile security);
	public boolean isFull();
	public QSeat[] getSeatInfoList(QSeat _user);
	void removeUser(QSeat user);
	public void say(QSeat user, String text);
	public void addEventListener(RoomEventListener listener);
	public void removeEventListener(RoomEventListener listener);
	public boolean isPublicRoom();
	public boolean isLocked();
	public GameDelegate createGame(int id, String name, String type,boolean systemPickOrder);
	public void removeUserFromGame(QHandle handle);
	public GameDelegate getGame(QSeat user);
	public QSeat getSeatInfo(QHandle handle);
	public void destroyGame(GameDelegate game);
	public GameInfo[] getGameInfoList();
	public QSeat addUserToGame(QHandle handle, GameDelegate game) throws UserNotInRoomException;
	public String getInfo();
	public ObservedGame observeGame(QHandle handle);
	public QSeat[] getExtSeatInfoList(QSeat user);
	public QSeat changeUserName(QSeat user, QHandle handle, ChatProfile profile);
	public boolean isManagedRoom();
}
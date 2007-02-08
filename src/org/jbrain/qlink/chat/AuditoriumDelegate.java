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
	Created on Jul 28, 2005
	
 */
package org.jbrain.qlink.chat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.apache.log4j.Logger;
import org.jbrain.qlink.db.DBUtils;
import org.jbrain.qlink.text.TextFormatter;

class Question {
	private String _sName;
	private String[] _question;
	
	public Question(String name, String[] question) {
		_sName=name;
		_question=question;
	}

	/**
	 * @return
	 */
	public String[] getQuestion() {
		return _question;
	}

	/**
	 * @return
	 */
	public String getHandle() {
		return _sName;
	}
	
}

class AuditoriumDelegate extends RoomDelegate {
	private static Logger _log=Logger.getLogger(AuditoriumDelegate.class);

	/**
	 * 
	 * @uml.property name="_queue"
	 * @uml.associationEnd elementType="org.jbrain.qlink.chat.Question" multiplicity="(0
	 * -1)"
	 */
	private List _queue = Collections.synchronizedList(new ArrayList());

	private boolean _bAcceptingQuestions = false;

	/**
	 * 
	 * @uml.property name="_alRegList"
	 * @uml.associationEnd elementType="java.lang.Integer" multiplicity="(0 -1)"
	 */
	private List _alRegList = Collections.synchronizedList(new ArrayList());

	/**
	 * 
	 * @uml.property name="_autoTalk"
	 * @uml.associationEnd inverse="this$0:org.jbrain.qlink.chat.AuditoriumDelegate$AutoText"
	 * multiplicity="(0 1)"
	 */
	private AutoText _autoTalk;

	/**
	 * 
	 * @uml.property name="_alViewers"
	 * @uml.associationEnd elementType="java.lang.String" multiplicity="(0 -1)"
	 */
	private List _alViewers = Collections.synchronizedList(new ArrayList());

 
	class AutoText extends Thread {
		//private String pad="                              ";
		private String _sKey;
		public AutoText(String key) {
			_sKey=key;
			//this.setDaemon(true);
			this.start();
		}
		
		public void run() {
		    Connection conn=null;
		    Statement stmt = null;
		    ResultSet rs = null;
		    
		    try {
		    	conn=DBUtils.getConnection();
		        stmt = conn.createStatement();
		        _log.debug("Reading auditorium text");
		    	rs=stmt.executeQuery("SELECT delay,text from auditorium_talk WHERE mnemonic LIKE '" + _sKey + "' order by sort_order");
		    	while(rs.next()) {
		    		say("Q-Link",rs.getString("text"));
		    		try {
						Thread.sleep(rs.getInt("delay")*1000);
					} catch (InterruptedException e) {
						_log.debug("AutoText timer was interrupted");
					}
		    	}
		    } catch (SQLException e) {
		    	_log.error("SQL Exception",e);
		    } finally {
		    	DBUtils.close(rs);
		    	DBUtils.close(stmt);
		    	DBUtils.close(conn);
		    }
			
		}
	}
	
	
	
	/**
	 * @param name
	 */
	public AuditoriumDelegate(String name) {
		super(name,true,true);
	}
	
	public boolean isFull() {
		return getPopulation()==ROOM_CAPACITY-1;
	}

	public synchronized void queue(String handle, String[] question) {
		_queue.add(new Question(handle,question));
		
		if(_alRegList.size()>0) {
			ArrayList alMsg=new ArrayList();
			privmsgQuestion(alMsg,_queue.size()-1);
			for(int i=0,size=_alRegList.size();i<size;i++) {
				sendSystemMessage(SYS_NAME,((Integer)_alRegList.get(i)).intValue(),alMsg);
			}
		}
	}
	
	public void removeUser(int seat) {
		super.removeUser(seat);
		_alRegList.remove(new Integer(seat));
	}
	
	public void say(int seat, String text) {
		if(text.startsWith("//") || text.startsWith("=q")) {
			processCommand(seat,text);
		} else
			super.say(seat,text);
	}

	private void say(String name,String text) {
	    TextFormatter tf =new TextFormatter(TextFormatter.FORMAT_PADDED,29);
	    List l;
	    String str;
	    StringBuffer sb=new StringBuffer();
	    
		tf.add(text);
		l=tf.getList();
		// spit out 3 lines as one chat text.
		for(int i=0,size=l.size();i<size;i++) {
			str=(String)l.get(i);
			_log.debug("appending: '" + str + "'");
			sb.append(str);
			if(i%3==2) {
				// send the string.
				processEvent(new ChatEvent(this,name,sb.toString()));
				name="";
				sb.setLength(0);
			} else if(i+1==size){
				processEvent(new ChatEvent(this,name,sb.toString()));
    			sb.setLength(0);
			} else {
				sb.append(" ");
			}
		}
	}
	
	/**
	 * @param seat
	 * @param text
	 */
	protected void processCommand(int seat, String text) {
		ArrayList alMsg=new ArrayList();
		String[] cmdline=text.split(" ");
		String cmd=cmdline[0].toLowerCase().substring(2);
		StringBuffer sb=new StringBuffer();
		int pos=0;
		if(cmd.startsWith("sho") || cmd.startsWith("air")) {
			// show
			if(cmdline.length>1)
				pos=getNumber(cmdline[1]);
			if(pos>-1 && pos < _queue.size()) {
				_log.debug("Showing question: " + pos);
				Question q=(Question)_queue.remove(pos);
				for(int i=0,size=q.getQuestion().length;i<size;i++) {
					sb.append(q.getQuestion()[i]);
					sb.append(" ");
				}
				say(q.getHandle(),sb.toString());
				sb.setLength(0);
			}
		} else if(cmd.startsWith("get")) {
			// get
			if(cmdline.length>1)
				pos=getNumber(cmdline[1]);
			if(pos>-1 && pos < _queue.size()) {
				_log.debug("Retrieving question: " + pos);
				privmsgQuestion(alMsg,pos);
				sendSystemMessage(SYS_NAME,seat,alMsg);
			}
		} else if(cmd.startsWith("del") || cmd.startsWith("rem")) {
			// delete
			if(cmdline.length>1)
				pos=getNumber(cmdline[1]);
			if(pos>-1 && pos < _queue.size()) {
				_log.debug("Deleting question: " + pos);
				Question q=(Question)_queue.remove(pos);
				alMsg.add("Question " + pos + " deleted.");
				sendSystemMessage(SYS_NAME,seat,alMsg);
			}
		} else if(cmd.startsWith("lis")) {
			// list
			if(cmdline.length>1)
				pos=getNumber(cmdline[1]);
			if(pos>-1 && pos < _queue.size()) {
				_log.debug("Listing questions starting with: " + pos);
				int max=pos+4;
				for(;pos<_queue.size()&&pos<max;pos++)
					privmsgQuestion(alMsg,pos);
				sendSystemMessage(SYS_NAME,seat,alMsg);
			}
		} else if(cmd.startsWith("cou")) {
			// count
			_log.debug("Sending count of queued questions: " + _queue.size());
			alMsg.add("There are " + _queue.size() + " questions in the queue.");
			sendSystemMessage(SYS_NAME,seat,alMsg);
		} else if(cmd.startsWith("cle")) {
			// clear Q
			_log.debug("Clearing question queue");
			_queue.clear();
			alMsg.add("Queue now empty.");
			sendSystemMessage(SYS_NAME,seat,alMsg);
		} else if(cmd.startsWith("acc")){
			_log.debug("Accepting questions");
			_bAcceptingQuestions=true;
			processEvent(new QuestionStateEvent(this,QuestionStateEvent.ACCEPTING_QUESTIONS));
		} else if(cmd.startsWith("rej")){
			_log.debug("Rejecting questions");
			_bAcceptingQuestions=false;
			processEvent(new QuestionStateEvent(this,QuestionStateEvent.NOT_ACCEPTING_QUESTIONS));
		} else if(cmd.startsWith("reg")){
			_log.debug("Registering for notifications");
			_alRegList.add(new Integer(seat));
		} else if(cmd.startsWith("unr")){
			_log.debug("De-Registering for notifications");
			_alRegList.remove(new Integer(seat));
		} else if(cmd.startsWith("auto")){
			_log.debug("Starting autotext");
			if(cmdline.length>1) {
				_log.debug("Sending auto text: " + cmdline[1]);
				_autoTalk=new AutoText(cmdline[1]);
			} else {
				alMsg.add("no key specified");
				sendSystemMessage(SYS_NAME,seat,alMsg);
			}
		} else {
			super.processCommand(seat,text);
		}
		
		
		// execture command
	}

	/**
	 * @param pos
	 */
	private void privmsgQuestion(List l, int pos) {
		Question q=(Question)_queue.get(pos);
		l.add("Question#: " + pos + " from " + q.getHandle() + ":");
		for(int i=0,size=q.getQuestion().length;i<size;i++)
			l.add(q.getQuestion()[i]);
	}

	/**
	 * @return
	 */
	public boolean isAcceptingQuestions() {
		return _bAcceptingQuestions;
	}

	public String getInfo() {
		// we'll grab the speaker information here.
	    Connection conn=null;
	    Statement stmt = null;
	    ResultSet rs = null;
	    StringBuffer sb=new StringBuffer();
	    
	    try {
	    	conn=DBUtils.getConnection();
	        stmt = conn.createStatement();
	        _log.debug("Reading auditorium text");
	    	rs=stmt.executeQuery("SELECT text from auditorium_text WHERE start_date<now() AND end_date>now()");
	    	while(rs.next()) {
	    		sb.append(rs.getString("text"));
	    		sb.append("\n");
	    	}
	    } catch (SQLException e) {
	    	_log.error("SQL Exception",e);
	    } finally {
	    	DBUtils.close(rs);
	    	DBUtils.close(stmt);
	    	DBUtils.close(conn);
	    }
	    return sb.toString();
	}
	
	protected void processEvent(RoomEvent event) {
		if(event instanceof QuestionStateEvent)
			processQuestionStateEvent((QuestionStateEvent)event);
		else
			super.processEvent(event);
	}
	
	public synchronized void processQuestionStateEvent(QuestionStateEvent event) {
		if(event != null && _listeners.size() > 0) {
			if(event.getType()==QuestionStateEvent.ACCEPTING_QUESTIONS)
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).acceptingQuestions(event);
				}
			else
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).rejectingQuestions(event);
				}
		}
	}

	public static void main(String a[]) {
		new AuditoriumDelegate("j").jim();
	}

	/**
	 * 
	 */
	private void jim() {
		try {
			DBUtils.init();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		new AutoText("common");
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param handle
	 */
	public void addViewer(String handle) {
		_alViewers.add(handle);
	}
	
	public void removeViewer(String handle) {
		_alViewers.remove(handle);
	}
}

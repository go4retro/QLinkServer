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
	Created on Jul 23, 2005
	
 */
package org.jbrain.qlink.state;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.jbrain.qlink.QServer;
import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.db.DBUtils;
import org.jbrain.qlink.io.EscapedInputStream;
import org.jbrain.qlink.text.TextFormatter;

class MenuEntry {

	private int _iID;
	private String _sTitle;
	private int _iType;
	private int _iCost;

	/**
	 * @param refid
	 * @param title
	 * @param type
	 * @param cost
	 */
	public MenuEntry(int id, String title, int type, int cost) {
		_iID=id;
		_sTitle=title;
		_iType=type;
		_iCost=cost;
	}

	/**
	 * @return
	 */
	public int getType() {
		return _iType;
	}

	/**
	 * @return
	 */
	public String getTitle() {
		return _sTitle;
	}

	/**
	 * @return
	 */
	public int getID() {
		return _iID;
	}

	/**
	 * @return
	 */
	public int getCost() {
		return _iCost;
	}
	
}

class MessageEntry {
	private String _sTitle;
	private Date _date;
	private int _iReplies=0;
	private String _sAuthor;
	private int _iID;
	private int _iReplyID=0;

	public MessageEntry(int id, String title, String author, Date date) {
		_iID=id;
		_sTitle=title;
		_date=date;
		_sAuthor=author;
	}

	/**
	 * @return
	 */
	public String getTitle() {
		return _sTitle;
	}

	/**
	 * @return
	 */
	public String getAuthor() {
		return _sAuthor;
	}

	/**
	 * @return
	 */
	public int getID() {
		return _iID;
	}

	/**
	 * @return
	 */
	public Date getDate() {
		return _date;
	}

	/**
	 * @param int1
	 */
	public void addReplyID(int id) {
		if(_iReplyID==0)
			_iReplyID=id;
		_iReplies++;
		
	}

	/**
	 * @return
	 */
	public int getReplies() {
		return _iReplies;
	}
}

public class DepartmentMenu extends AbstractState {
	private static Logger _log=Logger.getLogger(DepartmentMenu.class);
	private List _lText;

	/**
	 * 
	 * @uml.property name="_iLines"
	 * @uml.associationEnd elementType="java.lang.String" multiplicity="(0 -1)"
	 */
	private int _iLines;

	/**
	 * 
	 * @uml.property name="_alMessages"
	 * @uml.associationEnd elementType="org.jbrain.qlink.state.MessageEntry" multiplicity=
	 * "(0 -1)"
	 */
	private ArrayList _alMessages = new ArrayList();

	/**
	 * 
	 * @uml.property name="_hmMessages"
	 * @uml.associationEnd qualifier="new:java.lang.Integer org.jbrain.qlink.state.MessageEntry"
	 * multiplicity="(0 1)"
	 */
	private HashMap _hmMessages = new HashMap();

	/**
	 * 
	 * @uml.property name="_alMenu"
	 * @uml.associationEnd elementType="org.jbrain.qlink.state.MenuEntry" multiplicity=
	 * "(0 -1)"
	 */
	private ArrayList _alMenu = new ArrayList();

	private int _iCurrMenuID;
	private int _iCurrMessageID;
	private int _iNextMessageID;
	private int _iCurrParentID;
	private InputStream _is;
	private static final int XMIT_BLOCKS_MAX=16;
	 
	public DepartmentMenu(QServer server) {
		super(server);
		server.enableOLMs(true);
	}
	
	public void activate() throws IOException {
		_server.send(new MC());
		super.activate();
}
	/* (non-Javadoc)
	 * @see org.jbrain.qlink.state.QState#execute(org.jbrain.qlink.cmd.Command)
	 */
	public boolean execute(Action a) throws IOException {
		boolean rc=false;
		QState state;
		
		if(a instanceof SelectMenuItem) {
			int id=((SelectMenuItem)a).getID();
			selectItem(id);
		} else if(a instanceof ListSearch) {
			int id=((ListSearch)a).getID();
			int index=((ListSearch)a).getIndex();
			_log.debug("Received Search request with ID=" + id + " and index=" + index);
			//int bid=((MenuEntry)_alMenu.get(((ListSearch)a).getIndex())).getID();
			String q=((ListSearch)a).getQuery().replaceAll("'","\\\\'");
			selectMessageList(id,"AND (title LIKE '%" + q + "%' OR text LIKE '%" + q + "%')");
			_iLines=0;
			sendMessageList();
		} else if(a instanceof AbortDownload) {
			_log.debug("Client aborted download, closing InputStream");
			if(_is!=null)
				_is.close();
		} else if(a instanceof DownloadFile) {
			openFile(((DownloadFile)a).getID());
		} else if(a instanceof StartDownload) {
			_log.debug("Client requested download data");
			byte[] b=new byte[116];
			int len=-1;
			for(int i=0;i<XMIT_BLOCKS_MAX && (len=_is.read(b))>0;i++) {
				_server.send(new TransmitData(b,len,i==XMIT_BLOCKS_MAX-1?TransmitData.SAVE:TransmitData.SEND));
			}
			if(len<0) {
				_log.debug("Download completed, closing stream and sending EOF to client.");
				_server.send(new TransmitData(b,0,TransmitData.END));
				_is.close();
			}
			
		} else if(a instanceof SelectDateReply) {
			int id=((SelectDateReply)a).getID();
			Date date=((SelectDateReply)a).getDate();
			_log.debug("User requested next reply after " + date);
			// need to search for next reply, and send.
			id=selectDatedReply(id,date);
			displayMessage(id);
		} else if(a instanceof SelectList) {
			int id=((SelectList)a).getID();
			selectMessageList(id,"");
			_iLines=0;
			sendMessageList();
		} else if(a instanceof GetMenuInfo) {
				int id=((GetMenuInfo)a).getID();
				selectItem(id);
		} else if(a instanceof EnterMessageBoard) {
			// seems strange that we get this and that we need to send ANOTHER MC back.
			_server.send(new MC());
			if(checkEmail())
				_server.send(new NewMail());
			rc=true;
		} else if(a instanceof MR) {
			rc=true;
			state=new Chat(_server);
			state.activate();
		} else if(a instanceof SelectMoreList) {
			sendMessageList();
		} else if(a instanceof AbortSelectList) {
			clearMessageList();
		} else if(a instanceof FileTextAck) {
			sendPackedLines();
		} else if(a instanceof RequestItemPost) {
			int id=((RequestItemPost)a).getID();
			int index=((RequestItemPost)a).getIndex();
			_log.debug("Received Post request with ID=" + id + " and index=" + index);
			int pid;
			int bid;
			if(id==_iCurrMessageID) {
				_log.debug("User requests a new reply");
				pid=_iCurrParentID;
				if(pid==0)
					pid=id;
			} else {
				_log.debug("User requests a new posting");
				 if(id!=_iCurrMenuID)
				 	selectMenu(id);
				 pid=0;
			}
			bid=((MenuEntry)_alMenu.get(((RequestItemPost)a).getIndex())).getID();
			state=new PostMessage(_server,bid,pid,_iNextMessageID);
			state.activate();
		}
		if(!rc)
			rc=super.execute(a);
		return rc;
	}
	
	/**
	 * @param id
	 */
	private void openFile(int id) throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Selecting file " + id + " for download");
	        rs=stmt.executeQuery("SELECT filetype, LENGTH(data) as length,data from files where reference_id=" + id);
	        if(rs.next()) {
	        	// get our File length.
	        	int mid=rs.getInt("length");
	        	String type=rs.getString("filetype");
	        	// get binary stream
	        	_is=new EscapedInputStream(rs.getBinaryStream("data"));
	        	DBUtils.close(rs);
	        	_server.send(new InitDownload(mid,type));
	        } else {
    			_log.error("File not found.");
	        	// TODO What should we do if we do not find anything?
	        }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// TODO What should we do if we do not find anything?
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}

	/**
	 * @param id
	 * @param date
	 * @return
	 */
	private int selectDatedReply(int id, Date date) {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Searching for reply after " + date);
	        rs=stmt.executeQuery("SELECT message_id, parent_id from messages where reference_id=" + id);
	        if(rs.next()) {
	        	// get our message ID.
	        	int mid=rs.getInt("message_id");
	        	// this should be the same as _iParentID, but to be sure.
	        	int pid=rs.getInt("parent_id");
	        	if(pid!=_iCurrParentID)
	        		_log.error("Select Dated Reply id " + id + " has parent=" + pid + ", but current ParentID value=" + _iCurrParentID);
	        	DBUtils.close(rs);
	        	SimpleDateFormat sdf=new SimpleDateFormat("MM/dd/yyyy");
	        	// now, look for new replies.
		        rs=stmt.executeQuery("SELECT reference_id from messages where parent_id=" + pid + " AND message_id > " + mid + " AND date > '" + sdf.format(date) + " LIMIT 1");
		        if(rs.next()) {
		        	id= rs.getInt("reference_id");
		        } else {
		        	_log.error("We did not find any replies after this date");
		        	// TODO What should we do if we do not find anything?
	        	}
	        } else {
    			_log.error("Reply Dated search did not locate reply.");
	        	// TODO What should we do if we do not find anything?
	        }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// TODO What should we do if we do not find anything?
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
    	return id;
	}

	private void selectItem(int id) throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Selecting Item: " + id);
	        rs=stmt.executeQuery("SELECT entry_type, cost FROM entry_types WHERE reference_id=" + id);
	        if(rs.next()) {
	        	int type=rs.getInt("entry_type");
	        	String cost=rs.getString("cost");
	        	switch(type) {
	        		case MenuItem.MENU:
	        			_log.debug("Item is a menu, sending new menu");
	        			selectMenu(id);
	        			sendMenu(id);
	        			break;
	        		case MenuItem.MESSAGE:
	        			_log.debug("Item is a message, display it");
	        			displayMessage(id);
	        			break;
	        		case MenuItem.TEXT:
	        		case MenuItem.MULTI_TEXT:
	        			_log.debug("Item is a file, display it");
	        			displayDBTextFile(id);
		        		break;
		        	case MenuItem.DOWNLOAD:
		        		_log.debug("Item is a download, display text");
		        		displayFileInfo(id);
	        		case MenuItem.GATEWAY:
	        			_log.debug("Item is a gateway, connect to it");
	        			connectToGateway(id);
		        		break;
		        	default:
		    			_log.error("Item has unknown type, what should we do?");
		        		break;
	        	}
	        } else {
    			_log.error("Item has no reference, what should we do?");
	        }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}
	
	
	
	/**
	 * @param id
	 * @throws IOException
	 */
	private void connectToGateway(int id) throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        String address;
        int port;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Get file information for  Gateway ID: " + id);
	        rs=stmt.executeQuery("SELECT address,port from gateways where gateway_id=" + id);
	        if(rs.next()) {
	        	address=rs.getString("address");
	        	port=rs.getInt("port");
	        	if(address==null || address.equals("")) {
	        		_log.debug("Gateway address is null or empty.");
	    			_server.send(new GatewayExit("Destination invalid"));
	        	} else {
	        		if (port==0)
	        			port=23;
					QState state=new GatewayState(_server,address,port);
					state.activate();
	        	}
	        } else {
        		_log.debug("Gateway record does not exist.");
    			_server.send(new GatewayExit("Destination invalid"));
	        }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
			_server.send(new GatewayExit("Server error"));
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
		
	}

	/**
	 * 
	 */
	private void displayFileInfo(int id) throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Get file information for FileID: " + id);
	        rs=stmt.executeQuery("SELECT name, filetype, description from files where reference_id=" + id);
	        if(rs.next()) {
	            TextFormatter tf=new TextFormatter(TextFormatter.FORMAT_NONE,39);
	            tf.add("      " + rs.getString("name"));
	            tf.add("     " + rs.getString("filetype"));
	        	String data=rs.getString("description");
	            _server.send(new InitDataSend(id,0,0));
	            tf.add(data);
	            tf.add("\n <<   PRESS F7 FOR DOWNLOAD MENU    >> ");
	            _lText=tf.getList();
	            _iLines=0;
	            sendSingleLines();
	        } else {
    			_log.error("Item has no reference, what should we do?");
	        }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}

	/**
	 * 
	 */
	private void sendMenu(int id) throws IOException {
		int i=0,size=_alMenu.size();
		MenuEntry m;
		
		while(i<size) {
			m=(MenuEntry)_alMenu.get(i++);
		   	if(m.getType()==MenuItem.MESSAGE_BASE) 
		    		_server.send(new MenuItem(m.getID(),m.getTitle(),m.getID(),i== size && id>=10));
		    	else 
		    		_server.send(new MenuItem(m.getID(),m.getTitle(),m.getType(),m.getCost(),i==size && id>=10));
		}
		if(size==0) {
			_log.error("Menu ID invalid.");
			_server.send(new MenuItem(0,"Menu not available at this time.",MenuItem.HEADING,MenuItem.COST_NO_CHARGE,false));
		}
		if(id<10 || size==0) {
			_server.send(new MenuItem(422,"    Q-Link Post Office (+)",MenuItem.POST_OFFICE,MenuItem.COST_PREMIUM,false));
			_server.send(new MenuItem(0,"    Move to Another Q-Link Department",MenuItem.AREA_MENU,MenuItem.COST_NO_CHARGE,true));
		}
		
	}

	/**
	 * @param id
	 */
	private void displayMessage(int id) throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        int next=0;
        int mid;
        int pid;
        TextFormatter tf=new TextFormatter(TextFormatter.FORMAT_NONE,39);
        
        _iCurrMessageID=id;
        _iNextMessageID=0;
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Querying for message");
            String text;
	        rs=stmt.executeQuery("SELECT parent_id, message_id,text FROM messages WHERE reference_id=" + id);
	        if(rs.next()) {
	        	text=rs.getString("text");
	        	mid=rs.getInt("message_id");
	        	pid=rs.getInt("parent_id");
	        	_iCurrParentID=pid;
	        	// are we a main message?
	        	if(pid==0)
	        		pid=id;
	        	DBUtils.close(rs);
	        	// are there any replies to either this message or it's parent?
	        	rs=stmt.executeQuery("SELECT reference_id FROM messages WHERE message_id>" + mid + " AND parent_id=" + pid );
	        	if(rs.next()) {
	        		_iNextMessageID=rs.getInt("reference_id");
	        	}
	        } else {
				_log.error("Message ID invalid.");
				text="Message Not Found";
        	}
        	DBUtils.close(rs);
	        // init data area
            _server.send(new InitDataSend(id,0,0,_iNextMessageID,0));
            tf.add(text);
            _lText=tf.getList();
            _iLines=0;
            sendSingleLines();
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}

	/**
	 * @param id
	 * @param url
	 */
	private void displayDBTextFile(int id) throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        boolean bData=false;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Querying for text file");
            String data;
            int prev=0,next=0;
	        rs=stmt.executeQuery("SELECT next_id,prev_id,data FROM articles WHERE article_id=" + id);
	        if(rs.next()) {
	        	prev=rs.getInt("prev_id");
	        	next=rs.getInt("next_id");
	        	data=rs.getString("data");
	        } else {
				_log.error("Article ID invalid.");
				data="File Not Found";
        	}
        	DBUtils.close(rs);
	        // init data area
            _server.send(new InitDataSend(id,prev,next));
            TextFormatter tf=new TextFormatter(TextFormatter.FORMAT_NONE,39);
            tf.add(data);
            if(next!=0)
            	tf.add("\n  <PRESS F7 AND SELECT \"GET NEXT ITEM\">");
            else
            	tf.add("\n            <PRESS F5 FOR MENU>");
            _lText=tf.getList();
            _iLines=0;
            sendSingleLines();
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}

	/**
	 * @throws IOException
	 * 
	 */
	private void sendSingleLines() throws IOException {
		int i=0;
        while(i++<18 && _iLines+1<_lText.size()) {
    		_server.send(new FileText((String)_lText.get(_iLines++) + (char)0x7f,false));
        }
        if(_iLines+1<_lText.size()) {
    		_server.send(new FileTextPing((String)_lText.get(_iLines++) + (char)0x7f));
        } else {
    		_server.send(new FileText((String)_lText.get(_iLines++) + (char)0x7f,true));
        }
	}

	private void sendPackedLines() throws IOException {
		int i=0;
		String line;
		StringBuffer sb=new StringBuffer();
		// we want to send up to 19 lines...
        while(i++<18 && _iLines+1<_lText.size()) {
        	line=(String)_lText.get(_iLines++);
        	if(sb.length() + 1 + line.length() > 117) {
        		// send packet.
        		_server.send(new FileText(sb.toString(),false));
        		sb.setLength(0);
        	}
        	// add line
    		sb.append(line);
    		sb.append((char)0x7f);
        }
        // we are guaranteed to have one more line to send, but may have data in buffer.
        if(sb.length()>0) {
        	// we have stored data, go ahead and send it.  We will only be sending 18 lines in this case.
    		_server.send(new FileTextPing(sb.toString()));
        } else if(_iLines+1<_lText.size()) {
    		_server.send(new FileTextPing((String)_lText.get(_iLines++) + (char)0x7f));
        } else {
    		_server.send(new FileText((String)_lText.get(_iLines++) + (char)0x7f,true));
        }
	}

	private void selectMenu(int id) throws IOException {
		boolean rc=false;
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        boolean bData=false;
        int type=0;
        String cost;
        int refid;
        String title;
        int iCost=MenuItem.COST_NORMAL;
        MenuEntry m;
        
        _alMenu.clear();
		_iCurrMenuID=id;
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Querying for menu");
	        rs=stmt.executeQuery("SELECT toc.reference_id,toc.title,entry_types.entry_type,entry_types.cost FROM toc,entry_types WHERE toc.reference_id=entry_types.reference_id and toc.menu_id=" + id + " AND toc.active='Y' ORDER by toc.sort_order");
	        while(rs.next()) {
	        	bData=true;
	        	type=rs.getInt("entry_types.entry_type");
	        	cost=rs.getString("entry_types.cost");
	        	refid=rs.getInt("toc.reference_id");
	        	title=rs.getString("toc.title");
	        	if(type!=MenuItem.HEADING)
	        		title="    " + title;
	        	if(cost.equals("PREMIUM")) {
	        		title=title+" (+)";
	        		iCost=MenuItem.COST_PREMIUM;
	        	} else if(cost.equals("NOCHARGE")) {
	        		iCost=MenuItem.COST_NO_CHARGE;
	        	}
	        	_alMenu.add(new MenuEntry(refid,title,type,iCost));
	        }
        	DBUtils.close(rs);
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}
	
	private void selectMessageList(int id, String query) {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        int num=0;
        MessageEntry m;
        
        int prev=0;
        int next=0;
        int mid=0;
        int pid;
        String author=null;
        String title=null;
        String text=null;
        Date date=null;
        int replies=0;
        
        _alMessages.clear();
        _hmMessages.clear();
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Selecting message list for message base " + id);
        	rs=stmt.executeQuery("SELECT reference_id,parent_id, title,author, date,replies from messages WHERE base_id=" + id + " " + query + " order by message_id");
        	while(rs.next()) {
        		pid=rs.getInt("parent_id");
    			mid=rs.getInt("reference_id");
        		if(pid!=0) {
        			m=(MessageEntry)_hmMessages.get(new Integer(pid));
        			if(m!= null)
        				m.addReplyID(mid);
        			else
        				_log.error("Reference ID: " + mid + "is an orphan?");
        			
        		} else {
	        		title=rs.getString("title");
	        		author=rs.getString("author");
	        		date=rs.getDate("date");
	        		m=new MessageEntry(mid,title,author, date);
	        		_alMessages.add(m);
	        		_hmMessages.put(new Integer(mid),m);
	        		num++;
        		}
        	}
            _log.debug(num + " message found in message base");
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}

	private void sendMessageList() throws IOException {
		// we send these backwards from the List.
		int i=0;
		MessageEntry m;
		PostingItem line;
		
		int size=_alMessages.size();
		if(size==0)
			_server.send(new PostingItem("No News (is good news)", PostingItem.LAST));
		else {
			while(i<4 && _iLines<size-1) {
				i++;
				_iLines++;
				m=(MessageEntry)_alMessages.get(size-_iLines);
				_server.send(new PostingItem(m.getID(),m.getTitle(),m.getAuthor(),m.getReplies(),m.getDate(),PostingItem.NEXT));
			}
			_iLines++;
			m=(MessageEntry)_alMessages.get(size-_iLines);
			if(_iLines==size) {
				// after the ++ above, we should be at end.
				_server.send(new PostingItem(m.getID(),m.getTitle(),m.getAuthor(),m.getReplies(),m.getDate(),PostingItem.LAST));
				clearMessageList();
			} else {
				_server.send(new PostingItem(m.getID(),m.getTitle(),m.getAuthor(),m.getReplies(),m.getDate(),PostingItem.NEXT));
				_server.send(new PostingItem("Use RETURN to go on, F5 to cancel.",PostingItem.PAUSE));
			}
		}
		
	}

	/**
	 * 
	 */
	private void clearMessageList() {
		_alMessages.clear();
	}

}

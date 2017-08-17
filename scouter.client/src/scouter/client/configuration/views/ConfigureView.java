/*
 *  Copyright 2015 the original author or authors. 
 *  @https://github.com/scouter-project/scouter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package scouter.client.configuration.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import scouter.client.Images;
import scouter.client.model.TextProxy;
import scouter.client.net.TcpProxy;
import scouter.client.server.Server;
import scouter.client.server.ServerManager;
import scouter.client.sorter.ColumnLabelSorter;
import scouter.client.util.ColoringWord;
import scouter.client.util.ConsoleProxy;
import scouter.client.util.CustomLineStyleListener;
import scouter.client.util.ExUtil;
import scouter.client.util.ImageUtil;
import scouter.lang.conf.ValueType;
import scouter.lang.pack.MapPack;
import scouter.lang.value.ListValue;
import scouter.net.RequestCmd;
import scouter.util.CastUtil;
import scouter.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class ConfigureView extends ViewPart {
	public final static String ID = ConfigureView.class.getName();
	
	private ArrayList<ColoringWord> defaultHighlightings;
	private HashSet<String> configKeyNames = new HashSet<>();
	
	private StyledText text;
	private String content;
	private int serverId;
	private int objHash;
	private String displayName;

	private volatile String selectedText = "";
	private volatile long selectedTime = 0L;
	private volatile int selectedX = 0;
	private volatile int selectedY = 0;
	
	Composite listComp;
	TableViewer viewer;
	Table table;
	Text searchTxt;
	
	TableColumnLayout tableColumnLayout;
	
	private Clipboard clipboard = new Clipboard(null);

	CustomLineStyleListener listener;
	
	boolean devMode;
	
	HashMap<String, String> descMap = new HashMap<String, String>();
	HashMap<String, ValueType> valueTypeMap = new HashMap<String, ValueType>();
	
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
		sashForm.SASH_WIDTH = 1;
		initialStyledText(sashForm);
		listComp = new Composite(sashForm, SWT.NONE);
		listComp.setLayout(new GridLayout(1, true));
		
		Composite searchComp = new Composite(listComp, SWT.NONE);
		searchComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		
		searchComp.setLayout(new GridLayout(2, false));
		
		Label searchLabel = new Label(searchComp, SWT.BORDER);
		searchLabel.setText("Filter : ");
		searchTxt = new Text(searchComp, SWT.BORDER);
		
		searchTxt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		searchTxt.setToolTipText("Search Key/Value");
		searchTxt.addMouseListener(new MouseListener() {
			int clicked;
			public void mouseUp(MouseEvent arg0) {
			}
			public void mouseDown(MouseEvent arg0) {
				clicked++;
				if (clicked == 10) {
					devMode = true;
					viewer.refresh();
				}
			}
			public void mouseDoubleClick(MouseEvent arg0) {
			}
		});
		searchTxt.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				String searchText = searchTxt.getText();
				if (StringUtil.isEmpty(searchText)) {
					viewer.setInput(configList);
				} else {
					searchText = searchText.toLowerCase();
					List<ConfObject> tempList = new ArrayList<ConfObject>();
					for (ConfObject data : configList) {
						String name = data.key.toLowerCase();
						String value = data.value.toLowerCase();
						if (name.contains(searchText) || value.contains(searchText)) {
							tempList.add(data);
						}
					}
					viewer.setInput(tempList);
				}
			}
		});

		Composite tableComp = new Composite(listComp, SWT.NONE);
		tableComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tableColumnLayout = new TableColumnLayout();
		tableComp.setLayout(tableColumnLayout);
		viewer = new TableViewer(tableComp, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		createColumns();
		table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		viewer.setContentProvider(new ArrayContentProvider());
	    viewer.setComparator(new ColumnLabelSorter(viewer));
	    viewer.addFilter(filter);
	    final DefaultToolTip toolTip = new DefaultToolTip(table, DefaultToolTip.RECREATE, true);
		toolTip.setFont(new Font(null, "Arial", 11, SWT.BOLD));
		//toolTip.setForegroundColor(Display.getCurrent().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		toolTip.setBackgroundColor(Display.getCurrent().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
	    table.addMouseListener(new MouseListener() {
			public void mouseUp(MouseEvent e) {
			}
			
			public void mouseDown(MouseEvent e) {
				toolTip.hide();
				StructuredSelection sel = (StructuredSelection) viewer.getSelection();
				Object o = sel.getFirstElement();
				if (o instanceof ConfObject) {
					String configName = ((ConfObject) o).key;
					String desc = descMap.get(configName);
					if (StringUtil.isNotEmpty(desc)) {
						toolTip.setText(desc);
						toolTip.show(new Point(e.x + 10, e.y + 20));
					}
				}
			}
			public void mouseDoubleClick(MouseEvent e) {
				StructuredSelection selection = (StructuredSelection)viewer.getSelection();
				if(selection == null)
					return;
				ConfObject confObject = (ConfObject)selection.getFirstElement();
				if(confObject != null){
					String configText = text.getText();
					if(configText == null || configText.indexOf(confObject.key) >=0 ){
						return;
					}
					String desc = descMap.get(confObject.key);
					if(StringUtil.isNotEmpty(desc)) {
						desc = desc.replace("\n", "\n#");
						text.setText(configText + "\n\n" + "#" + desc);
					}
					text.setText(text.getText() + "\n" + confObject.key + "=" + confObject.value);
				}				
			}
		});
	    
		table.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if(e.stateMask == SWT.CTRL || e.stateMask == SWT.COMMAND){
					if (e.keyCode == 'c' || e.keyCode == 'C') {
						TableItem[] items = table.getSelection();
						if (items == null || items.length < 1) {
							return;
						}
						StringBuffer sb = new StringBuffer();
						for (TableItem item : items) {
							String desc = descMap.get(item.getText(0));
							if(StringUtil.isNotEmpty(desc)) {
								sb.append("#").append(desc.replace("\n", "\n#")).append("\n");
							}
							sb.append(item.getText(0));
							sb.append("=");
							sb.append(item.getText(1));
							sb.append("\n");
						}
						clipboard.setContents(new Object[] {sb.toString()}, new Transfer[] {TextTransfer.getInstance()});
					}
				}
			}
		});

		Label bottomLabel = new Label(listComp, SWT.BORDER);
		bottomLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		bottomLabel.setFont(new Font(null, "Arial", 10, SWT.BOLD));
		
		bottomLabel.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		bottomLabel.setText(new StringBuilder()
				.append("- click for tooltip\n")
				.append("- dbl-click for copy && paste (or ctl+C)")
				.toString());
				
		sashForm.setWeights(new int[] {1, 1});
		sashForm.setMaximizedControl(null);
		initialToolBar();
	}

	public void setInput(int serverId){
		this.serverId = serverId;
		Server server = ServerManager.getInstance().getServer(serverId);
		if (server != null) {
			this.displayName = server.getName();
			setPartName("Config Server[" + server.getName() + "]");
			loadConfig(RequestCmd.GET_CONFIGURE_SERVER, null);
			loadConfigList(RequestCmd.LIST_CONFIGURE_SERVER, null);
			loadConfigDesc(new MapPack());
			loadConfigValueType(new MapPack());
		}
	}
	
	public void setInput(int serverId, int objHash){
		this.serverId = serverId;
		this.objHash = objHash;
		Server server = ServerManager.getInstance().getServer(serverId);
		if (server != null) {
			this.displayName = TextProxy.object.getText(objHash);
			setPartName("Config Agent[" + TextProxy.object.getText(objHash) + "]");
			MapPack param = new MapPack();
			param.put("objHash", objHash);
			loadConfig(RequestCmd.GET_CONFIGURE_WAS, param);
			loadConfigList(RequestCmd.LIST_CONFIGURE_WAS, param);
			loadConfigDesc(param);
			loadConfigValueType(param);
		}
	}
	
	private void initialStyledText(Composite parent) {
		text = new StyledText(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		listener = new CustomLineStyleListener(true, defaultHighlightings, false);
		text.addLineStyleListener(listener);
		text.addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
			}
			public void keyPressed(KeyEvent e) {
				if(e.stateMask == SWT.CTRL || e.stateMask == SWT.COMMAND){
					if(e.keyCode == 's' || e.keyCode == 'S'){
						saveConfigurations();
					}else if(e.keyCode == 'a' || e.keyCode == 'A'){
						text.selectAll();
					}
				}
			}
		});
		text.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				if (selectedTime > System.currentTimeMillis() - 1500) {
					if (configKeyNames.contains(selectedText)) {
						System.out.println("click-selected-config : " + selectedText);
						ConfigureItemDialog dialog = new ConfigureItemDialog(parent.getShell(), selectedText, displayName);
						if (dialog.open() == Window.OK) {
							setTheConfig(selectedText, dialog.getValue());
						}
					}
				}
			}
		});
		text.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if(e.x == e.y) return;
				selectedText = text.getText(e.x, e.y-1);
				selectedTime = System.currentTimeMillis();
				selectedX = e.x;
				selectedY = e.y;
			}
		});
	}

	private void setTheConfig(String confKey, String confValue) {
		if ("null".equals(confValue)) {
			confValue = "";
		}

		String content = text.getText();
		String expression = "(?m)^" + confKey + "\\s*=.*\\n?";
		String replacement =  confKey + "=" + confValue + "\n";
		content = content.replaceAll(expression, replacement);
		System.out.println(content);
	}

	private void saveConfigurations(){
		TcpProxy tcp = TcpProxy.getTcpProxy(serverId);
		try {
			MapPack param = new MapPack();
			param.put("setConfig", text.getText().replaceAll("\\\\", "\\\\\\\\"));
			MapPack out = null;
			if (objHash == 0) {
				out = (MapPack) tcp.getSingle(RequestCmd.SET_CONFIGURE_SERVER, param);
			} else {
				param.put("objHash", objHash);
				out = (MapPack) tcp.getSingle(RequestCmd.SET_CONFIGURE_WAS, param);
			}
			
			if (out != null) {
				String config = out.getText("result");
				if("true".equalsIgnoreCase(config)) {
					MessageDialog.open(MessageDialog.INFORMATION
							, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()
							, "Success"
							, "Configuration saving is done."
							, SWT.NONE);
					if (objHash == 0) {
						loadConfigList(RequestCmd.LIST_CONFIGURE_SERVER, null);
					} else {
						MapPack param2 = new MapPack();
						param2.put("objHash", objHash);
						loadConfigList(RequestCmd.LIST_CONFIGURE_WAS, param2);
					}
				} else {
					MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()
							,"Error"
							, "Configuration saving is failed.");
				}
			}
		} catch(Exception e){
			ConsoleProxy.errorSafe(e.toString());
		} finally {
			TcpProxy.putTcpProxy(tcp);
		}
	}
	
	private void initialToolBar() {
		IToolBarManager man = getViewSite().getActionBars().getToolBarManager();
		man.add(new Action("Save", ImageUtil.getImageDescriptor(Images.save)) {
			public void run() {
				saveConfigurations();
			}
		});
	}

	private void loadConfig(final String requestCmd, final MapPack param) {
		ExUtil.asyncRun(new Runnable() {
			public void run() {
				MapPack mpack = null;
				TcpProxy tcp = TcpProxy.getTcpProxy(serverId);
				try {
					mpack = (MapPack) tcp.getSingle(requestCmd, param);
				} finally {
					TcpProxy.putTcpProxy(tcp);
				}
				if (mpack != null) {
					ListValue configKey = mpack.getList("configKey");
					
					defaultHighlightings = new ArrayList<ColoringWord>();
					for(int inx = 0 ; configKey != null && inx < configKey.size(); inx++){
						defaultHighlightings.add(new ColoringWord(configKey.getString(inx), SWT.COLOR_BLUE, true));
						configKeyNames.add(configKey.getString(inx));
					}
					defaultHighlightings.add(new ColoringWord(";", SWT.COLOR_RED, true));
					
					if (objHash == 0) {
						content = mpack.getText("serverConfig");
					} else {
						content = mpack.getText("agentConfig");
					}
				}
				ExUtil.exec(text, new Runnable() {
					public void run() {
						listener.setKeywordArray(defaultHighlightings);
						text.setText(content);
					}
				});
			}
		});
	}
	
	ArrayList<ConfObject> configList = new ArrayList<ConfObject>();
	
	private void loadConfigList(final String requestCmd, final MapPack param) {
		ExUtil.asyncRun(new Runnable() {
			public void run() {
				MapPack pack = null;
				TcpProxy tcp = TcpProxy.getTcpProxy(serverId);
				try {
					pack = (MapPack) tcp.getSingle(requestCmd, param);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					TcpProxy.putTcpProxy(tcp);
				}
				if (pack != null) {
					final ListValue keyList = pack.getList("key");
					final ListValue valueList = pack.getList("value");
					final ListValue defaultList = pack.getList("default");
					configList.clear();
					for (int i = 0; i < keyList.size(); i++) {
						ConfObject obj = new ConfObject();
						String key = CastUtil.cString(keyList.get(i));
						String value = CastUtil.cString(valueList.get(i));
						String def = CastUtil.cString(defaultList.get(i));
						obj.key = key == null ? "" : key;
						obj.value = value == null ? "" : value;
						obj.def = def == null ? "" : def;
						configList.add(obj);
					}
					ExUtil.exec(listComp, new Runnable() {
						public void run() {
							searchTxt.notifyListeners(SWT.Modify, new Event());
						}
					});					
				}
			}
		});
	}
	
	private void loadConfigDesc(final MapPack param) {
		ExUtil.asyncRun(new Runnable() {
			public void run() {
				MapPack pack = null;
				TcpProxy tcp = TcpProxy.getTcpProxy(serverId);
				try {
					pack = (MapPack) tcp.getSingle(RequestCmd.CONFIGURE_DESC, param);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					TcpProxy.putTcpProxy(tcp);
				}
				if (pack != null) {
					Iterator<String> keys = pack.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						descMap.put(key, pack.getText(key));
					}
				}
			}
		});
	}

	private void loadConfigValueType(final MapPack param) {
		ExUtil.asyncRun(new Runnable() {
			public void run() {
				MapPack pack = null;
				TcpProxy tcp = TcpProxy.getTcpProxy(serverId);
				try {
					pack = (MapPack) tcp.getSingle(RequestCmd.CONFIGURE_VALUE_TYPE, param);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					TcpProxy.putTcpProxy(tcp);
				}
				if (pack != null) {
					Iterator<String> keys = pack.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						valueTypeMap.put(key, ValueType.of(pack.getInt(key)));
					}
				}
			}
		});
	}
	
	public void setFocus() {
		IStatusLineManager slManager= getViewSite().getActionBars().getStatusLineManager();
		slManager.setMessage("CTRL + S : save configurations, CTRL + A : select all text");
	}
	
	private void createColumns() {
		for (ConfEnum column : ConfEnum.values()) {
			TableViewerColumn c = createTableViewerColumn(column.getTitle(), column.getWidth(), column.getAlignment(), column.isNumber());
			ColumnLabelProvider labelProvider = null;
			switch (column) {
			case KEY:
				labelProvider = new ColumnLabelProvider() {
					public String getText(Object element) {
						if (element instanceof ConfObject) {
							return ((ConfObject) element).key;
						}
						return null;
					}
				};
				break;
			case VALUE:
				labelProvider = new ColumnLabelProvider() {
					public String getText(Object element) {
						if (element instanceof ConfObject) {
							return ((ConfObject) element).value;
						}
						return null;
					}
				};
				break;
			case DEFAULT:
				labelProvider = new ColumnLabelProvider() {
					public String getText(Object element) {
						if (element instanceof ConfObject) {
							return ((ConfObject) element).def;
						}
						return null;
					}
				};
				break;
			}
			if (labelProvider != null) {
				c.setLabelProvider(labelProvider);
			}
		}
	}
	
	private TableViewerColumn createTableViewerColumn(String title, int width, int alignment, final boolean isNumber) {
		final TableViewerColumn viewerColumn = new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setAlignment(alignment);
		tableColumnLayout.setColumnData(column, new ColumnWeightData(30, width, true));
		column.setData("isNumber", isNumber);
		column.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				ColumnLabelSorter sorter = (ColumnLabelSorter) viewer.getComparator();
				TableColumn selectedColumn = (TableColumn) e.widget;
				sorter.setColumn(selectedColumn);
			}
		});
		return viewerColumn;
	}
	
	ViewerFilter filter = new ViewerFilter() {
		public boolean select(Viewer viewer, Object parent, Object element) {
			if (devMode) return true;
			if (element instanceof ConfObject) {
				if (((ConfObject)element).key.startsWith("_")) {
					return false;
				}
			}
			return true;
		}
	};
}

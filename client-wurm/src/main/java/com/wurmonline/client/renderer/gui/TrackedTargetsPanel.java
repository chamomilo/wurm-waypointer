package com.wurmonline.client.renderer.gui;

import org.waypoints.next.i18n.Messages;
import org.waypoints.next.model.*;
import org.waypoints.next.ui.*;
import org.waypoints.next.service.WaypointDistance;
import java.time.Instant;
import java.text.Collator;
import java.util.*;
import org.chamomilo.wurm.ui.v1.UiDensity;

/** A catalogue view; its controller owns all queries, tracking and persistence. */
final class TrackedTargetsPanel extends WaypointerContentPanel implements ButtonListener,InputFieldListener {
    private final TrackingController controller;
    private final WaypointManagerController manager;
    private final boolean animals;
    private final Map<WButton,Runnable> actions=new HashMap<WButton,Runnable>();
    private final Set<WButton> fixedActions=new HashSet<WButton>();
    private final List<Runnable> statusUpdates=new ArrayList<Runnable>();
    private final WurmInputField search,minus;
    private final WurmArrayPanel<FlexComponent> rows;
    private final WurmScrollPanel scroll;
    private final WurmLabel status;
    private final WButton refreshButton;
    private final WaypointerButtonGroup targetActions;
    private final Map<String,Integer> actionWidths=new HashMap<String,Integer>();
    private final Map<WButton,Column> sortActions=new LinkedHashMap<WButton,Column>();
    private final List<UUID> displayOrder=new ArrayList<UUID>();
    private final WurmArrayPanel<FlexComponent> headers;
    private final Column[] columns;
    private final int[] columnWidths;
    private Column sortColumn;
    private boolean ascending=true;
    private enum Column { TRACK,NAME,DETAIL,POSITION,DISTANCE,LAST_SEEN,NAV,CLEAR }
    private boolean requested;
    private long revision=-1,nextRefresh;
    TrackedTargetsPanel(TrackingController controller,WaypointManagerController manager,boolean animals){
        super("waypointer.targets."+animals,false);this.controller=controller;this.manager=manager;this.animals=animals;
        String[] captions=Messages.texts(new String[]{"Track","Untrack","Nav","Stop","Clear last seen"});
        int trackWidth=Math.max(WaypointerButtonGroup.width(captions[0],16,UiDensity.HIGH,false),WaypointerButtonGroup.width(captions[1],16,UiDensity.HIGH,false));
        int navWidth=Math.max(WaypointerButtonGroup.width(captions[2],16,UiDensity.HIGH,false),WaypointerButtonGroup.width(captions[3],16,UiDensity.HIGH,false));
        int[] widths={trackWidth,trackWidth,navWidth,navWidth,WaypointerButtonGroup.width(captions[4],16,UiDensity.HIGH,false)};
        targetActions=new WaypointerButtonGroup("tracked-targets.actions",UiDensity.HIGH,WaypointerTableActionCell.BUTTON_HEIGHT,captions,widths);
        for(int i=0;i<captions.length;i++)actionWidths.put(captions[i],widths[i]);
        columns=new Column[]{Column.TRACK,Column.NAME,Column.DETAIL,Column.POSITION,Column.DISTANCE,Column.LAST_SEEN,Column.NAV,Column.CLEAR};
        columnWidths=new int[columns.length];
        for(int i=0;i<columns.length;i++)columnWidths[i]=columnWidth(columns[i]);
        WurmArrayPanel<FlexComponent> top=new WurmArrayPanel<FlexComponent>("targets.top",0,true);
        search=WaypointerUi.input("targets.search.input",this);minus=WaypointerUi.input("targets.minus.input",this);
        WButton clearPlus=button("Clear",70),clearMinus=button("Clear",70);
        actions.put(clearPlus,()->search.setTextMoveToEnd(""));actions.put(clearMinus,()->minus.setTextMoveToEnd(""));
        top.addComponent(new WaypointerFilterRow("+ filter",search,clearPlus,"Example: horse, wolf"));
        top.addComponent(new WaypointerFilterRow("- filter",minus,clearMinus,"Example: catseyes, post"));
        refreshButton=button("Refresh",100);
        WaypointerButtonGroup.peers("tracked-targets.refresh",UiDensity.HIGH,28,refreshButton);
        refreshButton.setHoverString(Messages.text(animals?"Request your animals from the server.":"Request your land vehicles and ships from the server."));
        actions.put(refreshButton,()->controller.refresh(animals));
        WurmArrayPanel<FlexComponent> toolbar=new WurmArrayPanel<FlexComponent>("targets.toolbar",WurmArrayPanel.DIR_HORIZONTAL);
        toolbar.componentWidthOffset=6;
        toolbar.addComponent(refreshButton);toolbar.sizeFlags=FIXED_HEIGHT;top.addComponent(toolbar);
        fixedActions.addAll(actions.keySet());
        status=new WaypointerLabel("");top.addComponent(cell(status,800));setComponent(top,NORTH);
        headers=new WaypointerTableHeader.Row("tracked-targets.headers");
        // Native autoWidth temporarily sets the content height to zero while
        // resizing descendants, which makes Offsetter clamp its scroll offset.
        rows=new WurmArrayPanel<FlexComponent>("targets.rows",WurmArrayPanel.DIR_VERTICAL);scroll=new ChamomiloUiV1ScrollPanel("targets.scroll",rows,false,true);
        WurmBorderPanel table=new WurmBorderPanel("targets.table");table.setComponent(headers,NORTH);table.setComponent(scroll,CENTER);setComponent(table,CENTER);
        refresh(true);
    }
    private void refresh(boolean sortAgain){
        int offset=scroll.yo;UUID anchor=offset/28<displayOrder.size()?displayOrder.get(offset/28):null;
        rows.removeAllComponents();actions.keySet().retainAll(fixedActions);statusUpdates.clear();
        headers.removeAllComponents();sortActions.clear();
        String[] titles=new String[columns.length];for(int i=0;i<columns.length;i++)titles[i]=heading(columns[i]);
        int baseline=WaypointerTableHeader.baseline(titles);
        for(int i=0;i<columns.length;i++){
            Column column=columns[i];WaypointerTableHeader header=new WaypointerTableHeader("tracked-targets.headers",titles[i],columnWidths[i],sortColumn==column?(ascending?1:-1):0,baseline,column==Column.DISTANCE||column==Column.LAST_SEEN,this,hint(column)+" "+Messages.text("Click: ascending, descending, then original order."));
            headers.addComponent(header);sortActions.put(header,column);
        }
        headers.addComponent(new WurmPanel(3,28,false));
        WaypointManagerContext context=manager.context();
        org.waypoints.next.service.TextFilter filter=new org.waypoints.next.service.TextFilter(search.getText(),minus.getText());
        List<WaypointRecord> filtered=new ArrayList<WaypointRecord>();
        List<WaypointRecord> catalogue=controller.records(animals,"");
        for(WaypointRecord r:catalogue){
            String text=r.getName()+" "+controller.detail(r.getId())+" "+state(r.getResolution())+" "+Messages.text(state(r.getResolution()))+" "+r.getSourceKey();
            if(filter.matches(text))filtered.add(r);
        }
        if(sortAgain&&sortColumn!=null)filtered.sort(comparator(context));
        else if(!sortAgain){Map<UUID,Integer> previous=new HashMap<UUID,Integer>();for(int i=0;i<displayOrder.size();i++)previous.put(displayOrder.get(i),i);filtered.sort(Comparator.comparingInt(r->previous.getOrDefault(r.getId(),Integer.MAX_VALUE)));}
        displayOrder.clear();
        for(WaypointRecord record:filtered){displayOrder.add(record.getId());rows.addComponent(dataRow(record));}
        if(rows.components.isEmpty())rows.addComponent(cell(new WaypointerLabel(Messages.text(catalogue.isEmpty()?(animals?"No managed animals to display.":"No managed vehicles or ships to display."):"No targets match these filters.")),800));
        rows.componentResized();scroll.componentResized();int index=anchor==null?-1:displayOrder.indexOf(anchor);
        scrollTo(index<0?offset:index*28+offset%28);status.setLabel(Messages.text(controller.status()));revision=controller.revision();
    }
    private FlexComponent dataRow(WaypointRecord r){
        WurmArrayPanel<FlexComponent> row=new WurmArrayPanel<FlexComponent>("target."+r.getId(),WurmArrayPanel.DIR_HORIZONTAL);
        for(int i=0;i<columns.length;i++){
            Column column=columns[i];int width=columnWidths[i];FlexComponent component;
            if(column==Column.TRACK){
                WButton track=actionButton(r.isEnabled()?"Untrack":"Track");track.setEnabled(controller.canTrack(r.getId()));WaypointerButtonGroup.active(track,r.isEnabled());
                track.setHoverString(Messages.text(r.isEnabled()?"Remove this target from ALL WAYPOINTS; keep its catalogue entry and history.":animals?"Add this animal to ALL WAYPOINTS and automatically start a waypoint race if it is out of sight.":"Add this target to ALL WAYPOINTS and follow its received position."));
                actions.put(track,()->controller.setTracked(r.getId(),!r.isEnabled()));component=track;
            }else if(column==Column.NAV){
                WButton nav=actionButton(manager.isNavigatorActive(r.getId())?"Stop":"Nav");nav.setEnabled(manager.isNavigatorActive(r.getId())||controller.canNavigate(r.getId()));WaypointerButtonGroup.active(nav,manager.isNavigatorActive(r.getId()));
                nav.setHoverString(Messages.text(animals?"Navigate to this animal. If its position is unknown, start a waypoint race with automatic checks within 10 tiles of each search point.":"Navigate to this vehicle's confirmed or last known position."));
                actions.put(nav,()->manager.toggleNavigator(r.getId()));statusUpdates.add(()->{((ChamomiloUiV1Button)nav).setCaption(Messages.text(manager.isNavigatorActive(r.getId())?"Stop":"Nav"));WaypointerButtonGroup.active(nav,manager.isNavigatorActive(r.getId()));});component=nav;
            }else if(column==Column.CLEAR){
                WButton clear=actionButton("Clear last seen");clear.setEnabled(r.getResolution()!=WaypointResolution.LIVE_EXACT&&r.getLastResolvedAt()!=null);clear.setHoverString(Messages.text("Forget this target's saved position while retaining its catalogue entry."));actions.put(clear,()->controller.clearLastSeen(r.getId()));component=clear;
            }else{
                WaypointerTableLabel label=new WaypointerTableLabel(value(column,r,manager.context()),hint(column),false,column==Column.DISTANCE||column==Column.LAST_SEEN);
                label.setHint(hint(column)+" "+describe(r,manager.context())+" · "+r.getName()+" ["+r.getSourceKey()+"]");
                statusUpdates.add(()->{label.setLabel(value(column,r,manager.context()));label.setHint(hint(column)+" "+describe(r,manager.context())+" · "+r.getName()+" ["+r.getSourceKey()+"]");});component=cell(label,width);
            }
            if(component instanceof WButton)component=new WaypointerTableActionCell((WButton)component);
            row.addComponent(component);
            if(component.width<width){WurmPanel gap=new WurmPanel(width-component.width,28,false);gap.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;row.addComponent(gap);}
        }
        row.sizeFlags=FIXED_HEIGHT;return row;
    }
    private int columnWidth(Column column){
        int minimum;switch(column){case TRACK:minimum=actionWidths.get(Messages.text("Track"))+6;break;case NAV:minimum=actionWidths.get(Messages.text("Nav"))+6;break;case CLEAR:minimum=actionWidths.get(Messages.text("Clear last seen"))+6;break;case NAME:minimum=170;break;case DETAIL:minimum=88;break;case POSITION:minimum=130;break;case DISTANCE:minimum=80;break;case LAST_SEEN:minimum=88;break;default:throw new AssertionError(column);}
        return Math.max(minimum,org.chamomilo.wurm.ui.v1.UiTypography.width(Messages.text(heading(column)),16,true,UiDensity.HIGH)+28);
    }
    private String heading(Column column){switch(column){case TRACK:return "Track";case NAME:return "Name";case DETAIL:return "Type";case POSITION:return "Position";case DISTANCE:return "Distance";case LAST_SEEN:return "Updated";case NAV:return "Nav";case CLEAR:return "Clear last seen";default:throw new AssertionError(column);}}
    private String hint(Column column){switch(column){case NAME:return Messages.text("Full object name received from the client.");case DETAIL:return Messages.text("Managed object type received from the server.");case POSITION:return Messages.text("Position state. Visible now follows received movement; last known position is saved history; approximate direction has no exact coordinates.");case DISTANCE:return Messages.text("Straight-line distance in metres; a dash means coordinates are unavailable here.");case LAST_SEEN:return Messages.text("Age of the last received position or direction observation.");default:return Messages.text(heading(column));}}
    private String value(Column column,WaypointRecord r,WaypointManagerContext context){switch(column){case NAME:return r.getName();case DETAIL:return Messages.text(controller.detail(r.getId()).replaceFirst("^Manage ",""));case POSITION:return Messages.text(state(r.getResolution()));case DISTANCE:Integer distance=distance(r,context);return distance==null?"-":distance+" m";case LAST_SEEN:return r.getLastResolvedAt()==null?"-":age(r.getLastResolvedAt());default:throw new AssertionError(column);}}
    private static Integer distance(WaypointRecord r,WaypointManagerContext context){return r.getCoordinate()==null?null:WaypointDistance.metres(r.getCoordinate().getTileX(),r.getCoordinate().getTileY(),context.getTileX(),context.getTileY());}
    private Comparator<WaypointRecord> comparator(WaypointManagerContext context){
        final Collator collator=Collator.getInstance(Locale.forLanguageTag(Messages.language()));
        return (a,b)->{Object first=sortValue(a,context),second=sortValue(b,context);if(first==null||second==null)return first==second?0:first==null?1:-1;
            int result=first instanceof String?collator.compare(first,second):((Comparable)first).compareTo(second);return ascending?result:-result;};
    }
    private Object sortValue(WaypointRecord r,WaypointManagerContext context){switch(sortColumn){case TRACK:return r.isEnabled();case NAME:return r.getName();case DETAIL:return value(Column.DETAIL,r,context);case POSITION:return Messages.text(state(r.getResolution()));case DISTANCE:return distance(r,context);case LAST_SEEN:return r.getLastResolvedAt();case NAV:return manager.isNavigatorActive(r.getId());case CLEAR:return r.getResolution()!=WaypointResolution.LIVE_EXACT&&r.getLastResolvedAt()!=null;default:throw new AssertionError(sortColumn);}}
    private static String state(WaypointResolution value){switch(value){case LIVE_EXACT:return "Visible now";case LAST_SEEN:return "Last known position";case SERVER_BEARING:return "Approximate direction";case STALE:return "Stale";case SEARCH_STEP:return "Animal search point";default:return "Position unknown";}}
    private String describe(WaypointRecord record,WaypointManagerContext context){
        String description=Messages.text(state(record.getResolution()));
        if(record.getResolution()==WaypointResolution.SERVER_BEARING&&!record.getUncertaintyObservations().isEmpty()){
            UncertaintyObservation reading=record.getUncertaintyObservations().get(record.getUncertaintyObservations().size()-1);
            description+=" · "+Messages.format("Bearing {0}° ± {1}°; range {2}–{3} m from the observation point. {4} readings.",Math.round(reading.getBearingDegrees()),Math.round(reading.getHalfWidthDegrees()),Math.round(reading.getMinimumTiles()*4),Math.round(reading.getMaximumTiles()*4),record.getUncertaintyObservations().size());
        }
        if(record.getCoordinate()!=null)description+=" \u00b7 "+WaypointDistance.metres(record.getCoordinate().getTileX(),record.getCoordinate().getTileY(),context.getTileX(),context.getTileY())+" m";
        if(record.getLastResolvedAt()!=null&&record.getResolution()!=WaypointResolution.LIVE_EXACT)description+=" \u00b7 "+Messages.format("Last seen {0} ago",age(record.getLastResolvedAt()));
        return description+" \u00b7 "+Messages.text(controller.detail(record.getId()));
    }
    static String age(Instant observed){long seconds=Math.max(0,Instant.now().getEpochSecond()-observed.getEpochSecond());return seconds<60?Messages.format("{0} s",seconds):seconds<3600?Messages.format("{0} min",seconds/60):seconds<86400?Messages.format("{0} h",seconds/3600):Messages.format("{0} d",seconds/86400);}
    @Override public void gameTick(){super.gameTick();if(System.currentTimeMillis()<nextRefresh)return;nextRefresh=System.currentTimeMillis()+1000;if(revision!=controller.revision())refresh(false);else for(Runnable update:statusUpdates)update.run();status.setLabel(controller.status());}
    @Override public void buttonPressed(WButton button){}
    @Override public void buttonClicked(WButton button){
        if(sortActions.containsKey(button)){Column column=sortActions.get(button);if(sortColumn!=column){sortColumn=column;ascending=true;}else if(ascending)ascending=false;else sortColumn=null;refresh(true);scrollTo(0);return;}
        Runnable action=actions.get(button);if(action!=null&&button.isEnabled()){try{action.run();refresh(button==refreshButton);}catch(Throwable failure){manager.reportFailure("target action",failure);}}
    }
    @Override public void handleInput(String value){refresh(true);scrollTo(0);}
    @Override public void handleInputChanged(WurmInputField field,String value){refresh(true);scrollTo(0);}
    @Override public void handleEscape(WurmInputField field){}
    @Override void entered(){if(!requested){requested=true;controller.refresh(animals);}}
    @Override boolean hasInputField(){return true;}
    @Override WurmInputField getInputField(){return search;}
    @Override int minimumContentWidth(){int width=24;for(int column:columnWidths)width+=column;return width;}
    @Override boolean mouseWheeledAt(int x,int y,int delta){if(!scroll.contains(x,y))return false;scroll.mouseWheeled(x,y,delta);return true;}
    private WButton button(String label,int width){WButton button=WaypointerUi.button(Messages.text(label),this,width);button.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;return button;}
    private WButton actionButton(String label){String caption=Messages.text(label);int width=actionWidths.get(caption);ChamomiloUiV1Button button=(ChamomiloUiV1Button)button(label,width);targetActions.apply(button,width);return button;}
    private void scrollTo(int offset){((ChamomiloUiV1ScrollPanel)scroll).scrollTo(0,Math.max(0,offset));}
    private static FlexComponent cell(FlexComponent component,int width){component.setInitialSize(width,28,false);return component;}
}

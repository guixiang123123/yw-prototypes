package org.yesworkflow.graph;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.yesworkflow.config.YWConfiguration;
import org.yesworkflow.data.UriTemplate;
import org.yesworkflow.exceptions.YWToolUsageException;
import org.yesworkflow.model.Channel;
import org.yesworkflow.model.Model;
import org.yesworkflow.model.Port;
import org.yesworkflow.model.Program;

public class DotGrapher implements Grapher  {

    public static GraphView DEFAULT_GRAPH_VIEW = GraphView.PROCESS_CENTRIC_VIEW;
    public static CommentVisibility DEFAULT_COMMENT_VISIBILITY = CommentVisibility.HIDE;
    public static ParamVisibility DEFAULT_PARAM_VISIBILITY = ParamVisibility.HIDE;
    public static LayoutDirection DEFAULT_LAYOUT_DIRECTION = LayoutDirection.LR;
    public static WorkflowBoxMode DEFAULT_WORKFLOW_BOX_MODE = WorkflowBoxMode.SHOW;
    public static PortLayout DEFAULT_PORT_LAYOUT = PortLayout.GROUP;
    public static DataLabelMode DEFAULT_URI_DISPLAY_MODE = DataLabelMode.BOTH;
    public static EdgeLabelMode DEFAULT_EDGE_LABEL_MODE = EdgeLabelMode.SHOW;
    public static TitlePosition DEFAULT_TITLE_POSITION = TitlePosition.TOP;
    
    private Program topWorkflow = null;
    private Program workflow = null;
    private GraphView graphView = DEFAULT_GRAPH_VIEW;
    
    private ParamVisibility paramVisibility = DEFAULT_PARAM_VISIBILITY;
    private CommentVisibility commentView = DEFAULT_COMMENT_VISIBILITY;
    private LayoutDirection layoutDirection = DEFAULT_LAYOUT_DIRECTION;
    private WorkflowBoxMode workflowBoxMode = DEFAULT_WORKFLOW_BOX_MODE;
    private PortLayout portLayout = DEFAULT_PORT_LAYOUT;
    private DataLabelMode uriDisplayMode = DEFAULT_URI_DISPLAY_MODE;
    private EdgeLabelMode edgeLabelMode = DEFAULT_EDGE_LABEL_MODE;
    private TitlePosition titlePosition = DEFAULT_TITLE_POSITION;
    private String title = null;
    private String subworkflowName = null;
    private String graphText = null;
    private String outputDotFile = null;
    private PrintStream stdoutStream = null;
    private List<String> channelBindings = new LinkedList<String>();
        
    @SuppressWarnings("unused")
    private PrintStream stderrStream = null;
    
    public DotGrapher(PrintStream stdoutStream, PrintStream stderrStream) {
        this.stdoutStream = stdoutStream;
        this.stderrStream = stderrStream;
    }

    @Override
    public DotGrapher model(Model model) {        
        if (model == null) throw new IllegalArgumentException("Null model passed to DotGrapher.");
        if (model.program == null) throw new IllegalArgumentException("Model with null program passed to DotGrapher.");
        this.topWorkflow = model.program;
        return this;
    }

    @Override
    public DotGrapher workflow(Program workflow) {
        if (workflow == null) throw new IllegalArgumentException("Null workflow passed to DotGrapher.");
        this.topWorkflow = workflow;
        return this;
    }
    
    @Override
    public DotGrapher configure(Map<String,Object> config) throws Exception {
        if (config != null) {
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                configure(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }
    
    public DotGrapher configure(String key, Object value) throws Exception {
        if (key.equalsIgnoreCase("view")) { 
            graphView = GraphView.toGraphView(value);
        } else if (key.equalsIgnoreCase("dotcomments")) {
            commentView = CommentVisibility.toCommentVisibility(value);
        } else if (key.equalsIgnoreCase("params")) {
            paramVisibility = ParamVisibility.toParamVisibility(value);
        } else if (key.equalsIgnoreCase("layout")) {
            layoutDirection = LayoutDirection.toLayoutDirection(value);
        } else if (key.equalsIgnoreCase("workflowbox")) {
            workflowBoxMode = WorkflowBoxMode.toWorkflowBoxMode(value);
        } else if (key.equalsIgnoreCase("portlayout")) {
            portLayout = PortLayout.toPortLayout(value);
        } else if (key.equalsIgnoreCase("datalabel")) {
            uriDisplayMode = DataLabelMode.toUriDisplayMode(value);
        } else if (key.equalsIgnoreCase("edgelabels")) {
            edgeLabelMode = EdgeLabelMode.toEdgeLabelMode(value);
        } else if (key.equalsIgnoreCase("subworkflow")) {
            subworkflowName = (String)value;
        } else if (key.equalsIgnoreCase("title")) {
            title = (String)value;
        } else if (key.equalsIgnoreCase("titleposition")) {
            titlePosition = TitlePosition.toTitlePosition(value);
        } else if (key.equalsIgnoreCase("dotfile")) {
            outputDotFile = (String)value;
        }
        
        return this;
    }
    
    
	public String toString() {
        return graphText;
    }
    
    @Override
    public DotGrapher graph() throws Exception {
        
        if (topWorkflow == null) throw new YWToolUsageException("Workflow not identified to DotGrapher.");
        workflow = (subworkflowName == null) ? topWorkflow : topWorkflow.getSubprogram(subworkflowName);
        if (workflow == null) throw new YWToolUsageException("Subworkflow named " + subworkflowName + " not found.");
        
        switch(graphView) {
        
        case PROCESS_CENTRIC_VIEW:
            this.graphText = new ProcessRendering().render();
            break;
        
        case DATA_CENTRIC_VIEW:
            this.graphText = new DataRendering().render();
            break;
        
        case COMBINED_VIEW:
            this.graphText = new CombinedRendering().render();
            break;
        }
        
        writeTextToFileOrStdout(outputDotFile, this.graphText);
        return this;
    }
    
    private void writeTextToFileOrStdout(String path, String text) throws IOException {        
        PrintStream stream = (path == null || path.equals(YWConfiguration.EMPTY_VALUE) || path.equals("-")) ?
                             this.stdoutStream : new PrintStream(path);
        stream.print(text);
        if (stream != this.stdoutStream) {
            stream.close();
        }
    }
        
    private abstract class GraphRendering {
        
        protected DotBuilder dot;
        
        public GraphRendering() {
            dot = new DotBuilder().beginGraph()
                                  .rankDir(layoutDirection.toString())
                                  .enableComments(commentView == CommentVisibility.SHOW)
                                  .showClusterBox(workflowBoxMode == WorkflowBoxMode.SHOW);
        }
        
        public abstract String render();

        protected void beginRendering() {
            dot = new DotBuilder().beginGraph()
                    .rankDir(layoutDirection.toString())
                    .enableComments(commentView == CommentVisibility.SHOW)
                    .showClusterBox(workflowBoxMode == WorkflowBoxMode.SHOW);
        }
        
        protected void endRendering() {
            dot.endGraph();
        }

        
        @SuppressWarnings("incomplete-switch")
        protected void drawWorkflowBoxAndTitle() {

            if (title == null) {
                title = workflow.toString();
            }
            
            if (titlePosition != TitlePosition.HIDE) {
                dot.comment("Title for graph");
                switch(titlePosition) {
                case BOTTOM:
                    dot.title(title, "b");
                    break;
                case TOP:
                    dot.title(title.toString(), "t");
                    break;
                }
            }
            
            dot.comment("Start of cluster for drawing box around programs in workflow");
            dot.beginSubgraph();
        }
        
        protected String edgeLabel(String label) {
            return (edgeLabelMode == EdgeLabelMode.SHOW) ? label : "";
        }
        
        protected void renderInputAndOutputPorts(Program workflow) {
            
            // draw a small circle for each outward facing in and out port
            dot.shape("circle").peripheries(1).width(0.2).fillcolor("#FFFFFF");
            dot.flushNodeStyle();

            if (portLayout == PortLayout.GROUP) dot.beginHiddenSubgraph();
            dot.comment("Nodes representing workflow input ports");
            for (Port p : workflow.inPorts) {
                String binding = p.flowAnnotation.binding();
                if (channelBindings.contains(binding)) {
                    dot.node(binding + "_inport", "");
                }
            }
            if (portLayout == PortLayout.GROUP) dot.endSubgraph();
            
            if (portLayout == PortLayout.GROUP) dot.beginHiddenSubgraph();
            dot.comment("Nodes representing workflow output ports");
            for (Port p : workflow.outPorts) {
                String binding = p.flowAnnotation.binding();
                if (channelBindings.contains(binding)) {
                    dot.node(binding + "_outport", "");
                }
            }
            if (portLayout == PortLayout.GROUP) dot.endSubgraph();
        }        

        protected void drawChannelNodes() {

            switch(paramVisibility) {

            case SHOW:
                dot.shape("box").fillcolor("#FFFFCC").style("rounded,filled");
                for (Channel c : workflow.innerChannels()) {
                    drawChannelNode(c);
                }        
            case REDUCE:
                dot.shape("box").fillcolor("#FFFFCC").style("rounded,filled");
                for (Channel c : workflow.innerDataChannels()) {
                    drawChannelNode(c);
                }
                dot.shape("box").fillcolor("#FCFCFC").style("rounded,filled");
                for (Channel c : workflow.innerParamChannels()) {
                    drawChannelNode(c);
                }
            case HIDE:
                dot.shape("box").fillcolor("#FFFFCC").style("rounded,filled");
                for (Channel c : workflow.innerDataChannels()) {
                    drawChannelNode(c);
                }
            }
        }
        
        protected void drawChannelNode(Channel c) {
            
            String binding = c.sourcePort.flowAnnotation.binding();
            channelBindings.add(binding);
            UriTemplate uri = c.sourcePort.uriTemplate;
            
            if (uri == null) {
                dot.node(binding);
            } else {
                String uriLabel = uri.toString().replace("{", "\\{").replace("}", "\\}");
                switch(uriDisplayMode) {
                    case NAME: 
                        dot.node(binding);
                        break;
                    case URI:
                        dot.node(binding, uriLabel);
                        break;
                    case BOTH:
                        dot.recordNode(binding, binding, uriLabel);
                        break;
                }
                dot.node(binding);
            }
        }
        
        protected void drawEdgesFromPortsToChannelNodes() {
            
            if (portLayout != PortLayout.HIDE) {
                
                // draw an edge from each workflow input to the corresponding channel node
                for (Port p : workflow.inPorts) {
                    String binding = p.flowAnnotation.binding();
                    if (channelBindings.contains(binding)) {
                        dot.edge(binding + "_inport", binding);
                    }
                }
                
                // draw an edge from each workflow output to the corresponding channel node
                for (Port p : workflow.outPorts) {
                    String binding = p.flowAnnotation.binding();
                    if (workflow.hasChannelForBinding(binding)) {
                        dot.edge(binding, binding + "_outport");
                    }
                }
            }
        }
    }
    
    private class ProcessRendering extends GraphRendering {

        @Override
        public String render() {
            
            beginRendering();
            
            dot.comment("Use serif font for process labels and sans serif font for data labels");
            dot.graphFont("Courier")
               .edgeFont("Helvetica")
               .nodeFont("Courier");

            drawWorkflowBoxAndTitle();
            
            dot.comment("Set node style for programs in workflow");
            dot.shape("box3d").peripheries(1).fillcolor("#CCFFCC");      
            dot.flushNodeStyle();
            
            dot.comment("Nodes representing programs in workflow");
            for (Program p : workflow.programs) {
                if (! (p.isWorkflow())) {
                    dot.node(p.beginAnnotation.name);
                    if (paramVisibility != ParamVisibility.HIDE) {
                        channelBindings.addAll(p.outerBindings());
                    } else {
                        channelBindings.addAll(p.outerDataBindings());
                    }
                }
            }

            dot.comment("Set node style for subworkflows in workflow");
            dot.shape("box").peripheries(2).fillcolor("#CCFFCC");      
            dot.flushNodeStyle();

            dot.comment("Nodes representing subworkflows in workflow");
            dot.shape("box").peripheries(2).fillcolor("#CCFFCC");   
            for (Program p : workflow.programs) {
                if (p.isWorkflow()) {
                    dot.node(p.beginAnnotation.name);
                    if (paramVisibility == ParamVisibility.SHOW) {
                        channelBindings.addAll(p.outerBindings());
                    } else {
                        channelBindings.addAll(p.outerDataBindings());
                    }
                }
            }

            dot.comment("End of cluster for drawing box around programs in workflow");
            dot.endSubgraph();
            
            if (portLayout != PortLayout.HIDE) {
                renderInputAndOutputPorts(workflow);
            }
            
            dot.comment("Directed edges for each channel in workflow");
            for (Channel c : workflow.channels) {
                
                if (c.isParam && paramVisibility == ParamVisibility.HIDE) continue;
                
                Program sourceProgram = c.sourceProgram;
                Program sinkProgram = c.sinkProgram;
                
                // draw edges for channels between workflow in ports and programs in workflow
                if (sourceProgram == null) {
                    
                    if (portLayout != PortLayout.HIDE) {
                    
                        dot.edge(c.sinkPort.flowAnnotation.binding() + "_inport",
                                 c.sinkProgram.beginAnnotation.name,
                                 edgeLabel(c.sinkPort.flowAnnotation.binding()));
                    }
                    
                // draw edges for channels between programs in workflow and workflow out ports
                } else if (sinkProgram == null) {
                    
                    if (portLayout != PortLayout.HIDE) {

                        dot.edge(c.sourceProgram.beginAnnotation.name,
                             c.sourcePort.flowAnnotation.binding() + "_outport",
                             edgeLabel(c.sourcePort.flowAnnotation.binding()));
                    }
                    
                // draw edges for channels between programs within workflow
                } else {

                    dot.edge(c.sourceProgram.beginAnnotation.name,
                             c.sinkProgram.beginAnnotation.name,
                             edgeLabel(c.sourcePort.flowAnnotation.binding()));
                }
            }
            
            endRendering();
            return dot.toString();
        }
    }
    
    private class DataRendering extends GraphRendering {

        @Override
        public String render() {

            beginRendering();
            
            drawWorkflowBoxAndTitle();

            dot.comment("Use serif font for process labels and sans serif font for data labels");
            dot.graphFont("Courier")
               .edgeFont("Courier")
               .nodeFont("Helvetica");
            
            // draw a box for each channel in the workflow
            dot.shape("box").fillcolor("#FFFFCC").style("rounded,filled");

            drawChannelNodes();

            dot.comment("End of cluster for drawing box around programs in workflow");
            dot.endSubgraph();

            if (portLayout != PortLayout.HIDE) {
                renderInputAndOutputPorts(workflow);
            }
            
            drawEdgesFromPortsToChannelNodes();
            
            // draw an edge for each pairing of out port and in port for each program
            for (Program p : workflow.programs) {
                for (Port out : p.outPorts) {
                    for (Port in : p.inPorts) {
                        
                        if (channelBindings.contains(in.flowAnnotation.binding()) && channelBindings.contains(out.flowAnnotation.binding())) {
                            dot.edge(
                                in.flowAnnotation.binding(), 
                                out.flowAnnotation.binding(), 
                                edgeLabel(p.beginAnnotation.name)
                            );
                        }
                    }
                }
            }
            
            endRendering();
            return dot.toString();
        }
    }
    
    
    private class CombinedRendering extends GraphRendering {
        
        @Override
        public String render() {
            
            beginRendering();
            
            drawWorkflowBoxAndTitle();
            
            dot.comment("Use serif font for process labels");
            dot.graphFont("Courier")
               .nodeFont("Courier");
            
            // draw a box for each program in the workflow
            dot.shape("box3d").fillcolor("#CCFFCC");
            for (Program p : workflow.programs) dot.node(p.beginAnnotation.name);
            
            dot.comment("Use sans serif font for data labels");
            dot.nodeFont("Helvetica");

            drawChannelNodes();
            
            dot.comment("End of cluster for drawing box around programs in workflow");
            dot.endSubgraph();

            if (portLayout != PortLayout.HIDE) {
                renderInputAndOutputPorts(workflow);
            }
            
            // draw an edge for each pairing of out port and in port for each program
            for (Program p : workflow.programs) {

                for (Port out : p.outPorts) {
                    
                    String binding = out.flowAnnotation.binding();
                    if (channelBindings.contains(binding)) {
                        dot.edge(p.beginAnnotation.name, binding);
                    }
                }

                for (Port in : p.inPorts) {
                    String binding = in.flowAnnotation.binding();
                    if (channelBindings.contains(binding)) {
                        dot.edge(binding, p.beginAnnotation.name);
                    }
                }
            }

            drawEdgesFromPortsToChannelNodes();  
            
            endRendering();
            return dot.toString();
        }
    }    
}


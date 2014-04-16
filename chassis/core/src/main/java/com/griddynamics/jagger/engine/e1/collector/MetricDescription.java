package com.griddynamics.jagger.engine.e1.collector;

import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.List;

/** Class to describe metric
 * @author Gribov Kirill
 * @n
 */
public class MetricDescription implements Serializable{

    protected String id;
    protected String displayName;
    protected boolean showSummary = true;
    protected boolean plotData;
    protected List<MetricAggregatorProviderWithSettings> aggregatorsWithSettings = Lists.newArrayList();

    /** Constructor
     * @param metricId - main ID of the metric. Metric will be stored under this ID in DB */
    public MetricDescription(String metricId) {
        this.id = metricId;
    }

    /** Getter for metric ID
     * @return Metric ID*/
    public String getMetricId() {
        return this.id;
    }
    /** Setter for metric ID
     * @param metricId - main ID of the metric. Metric will be stored under this ID in DB */
    public void setMetricId(String metricId){
        this.id = metricId;
    }

    /** Getter for metric display name
     * @return display name */
    public String getDisplayName() {
        return displayName;
    }
    /** Setter for metric display name
     * @param displayName - display name of the metric. This name will be displayed in WebUI and PDF report */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Getter for metric aggregators
     * @deprecated since 1.2.5 MetricDescription class contains list of wrappers of Aggregators. use getAggregatorsWithSettings;
     * @return list of aggregators assigned to this metric */
    @Deprecated
    public List<MetricAggregatorProvider> getAggregators() {
        List<MetricAggregatorProvider> result = Lists.newArrayListWithCapacity(aggregatorsWithSettings.size());
        for (MetricAggregatorProviderWithSettings aggregatorWithSettings : aggregatorsWithSettings) {
            result.add(aggregatorWithSettings.getAggregatorProvider());
        }
        return result;
    }
    /** Setter for metric aggregators
     * @param aggregators - list of aggregators that will be applied to this metric during result processing. @n
     *                      If list will be empty Jagger will use default aggregator (summary).@n
     *                      You can use Jagger built in aggregators @ref Main_Aggregators_group or custom aggregators */
    public void setAggregators(List<MetricAggregatorProvider> aggregators) {
        for (MetricAggregatorProvider aggregatorProvider : aggregators) {
            aggregatorsWithSettings.add(new MetricAggregatorProviderWithSettings(aggregatorProvider));
        }
    }

    public List<MetricAggregatorProviderWithSettings> getAggregatorsWithSettings() {
        return aggregatorsWithSettings;
    }

    public void setAggregatorsWithSettings(List<MetricAggregatorProviderWithSettings> aggregatorsWithSettings) {
        this.aggregatorsWithSettings = aggregatorsWithSettings;
    }

    /** Getter for metric "show summary" boolean parameter
     * @return true if necessary to save summary value to DB and show it in report and WebUI */
    public boolean getShowSummary() {
        return showSummary;
    }
    /** Setter for metric "show summary" boolean parameter
     * @param showSummary - set true if you want to save summary value to DB and show it in report and WebUI */
    public void setShowSummary(boolean showSummary) {
        this.showSummary = showSummary;
    }

    /** Getter for metric "plot data" boolean parameter
     * @return true if necessary to save detailed results (metric vs time) to DB and show it in report and WebUI */
    public boolean getPlotData() {
        return plotData;
    }
    /** Setter for metric "plot data" boolean parameter
     * @param plotData - set true if you want to save detailed results (metric vs time) to DB and show it in report and WebUI */
    public void setPlotData(boolean plotData) {
        this.plotData = plotData;
    }

    /** Setter for metric "show summary" boolean parameter
     * @param showSummary - set true if you want to save summary value to DB and show it in report and WebUI
     * @return this MetricDescription */
    public MetricDescription showSummary(boolean showSummary){
        this.showSummary = showSummary;
        return this;
    }
    /** Setter for metric "plot data" boolean parameter
     * @param plotData - set true if you want to save detailed results (metric vs time) to DB and show it in report and WebUI
     * @return this MetricDescription */
    public MetricDescription plotData(boolean plotData){
        this.plotData = plotData;
        return this;
    }
    /** Setter for metric display name
     * @param displayName - display name of the metric. This name will be displayed in WebUI and PDF report
     * @return this MetricDescription */
    public MetricDescription displayName(String displayName){
        this.displayName = displayName;
        return this;
    }

    /** Append new aggregator to list of metric aggregator
     * @param aggregator - aggregators that will be applied to this metric during result processing. @n
     *                     You can use Jagger built in aggregators @ref Main_Aggregators_group or custom aggregator
     * @return this MetricDescription */
    public MetricDescription addAggregator(MetricAggregatorProvider aggregator){
        aggregatorsWithSettings.add(new MetricAggregatorProviderWithSettings(aggregator));
        return this;
    }

    /** Append new aggregator to list of metric aggregator with interval of time to normalize plot data.
     * @param aggregator - aggregators that will be applied to this metric during result processing. @n
     *                     You can use Jagger built in aggregators @ref Main_Aggregators_group or custom aggregator
     * @param settings - settings of aggregator.
     * @return this MetricDescription */
    public MetricDescription addAggregator(MetricAggregatorProvider aggregator, MetricAggregatorProviderWithSettings.Settings settings){
        aggregatorsWithSettings.add(new MetricAggregatorProviderWithSettings(aggregator, settings));
        return this;
    }
}

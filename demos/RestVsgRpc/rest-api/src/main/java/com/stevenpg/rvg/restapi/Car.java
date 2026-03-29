
package com.stevenpg.rvg.restapi;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "make",
    "model",
    "trim",
    "color",
    "year",
    "mileage"
})
@Generated("jsonschema2pojo")
public class Car {

    @JsonProperty("make")
    private String make;
    @JsonProperty("model")
    private String model;
    @JsonProperty("trim")
    private String trim;
    @JsonProperty("color")
    private String color;
    @JsonProperty("year")
    private Integer year;
    @JsonProperty("mileage")
    private Integer mileage;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("make")
    public String getMake() {
        return make;
    }

    @JsonProperty("make")
    public void setMake(String make) {
        this.make = make;
    }

    public Car withMake(String make) {
        this.make = make;
        return this;
    }

    @JsonProperty("model")
    public String getModel() {
        return model;
    }

    @JsonProperty("model")
    public void setModel(String model) {
        this.model = model;
    }

    public Car withModel(String model) {
        this.model = model;
        return this;
    }

    @JsonProperty("trim")
    public String getTrim() {
        return trim;
    }

    @JsonProperty("trim")
    public void setTrim(String trim) {
        this.trim = trim;
    }

    public Car withTrim(String trim) {
        this.trim = trim;
        return this;
    }

    @JsonProperty("color")
    public String getColor() {
        return color;
    }

    @JsonProperty("color")
    public void setColor(String color) {
        this.color = color;
    }

    public Car withColor(String color) {
        this.color = color;
        return this;
    }

    @JsonProperty("year")
    public Integer getYear() {
        return year;
    }

    @JsonProperty("year")
    public void setYear(Integer year) {
        this.year = year;
    }

    public Car withYear(Integer year) {
        this.year = year;
        return this;
    }

    @JsonProperty("mileage")
    public Integer getMileage() {
        return mileage;
    }

    @JsonProperty("mileage")
    public void setMileage(Integer mileage) {
        this.mileage = mileage;
    }

    public Car withMileage(Integer mileage) {
        this.mileage = mileage;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public Car withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Car.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("make");
        sb.append('=');
        sb.append(((this.make == null)?"<null>":this.make));
        sb.append(',');
        sb.append("model");
        sb.append('=');
        sb.append(((this.model == null)?"<null>":this.model));
        sb.append(',');
        sb.append("trim");
        sb.append('=');
        sb.append(((this.trim == null)?"<null>":this.trim));
        sb.append(',');
        sb.append("color");
        sb.append('=');
        sb.append(((this.color == null)?"<null>":this.color));
        sb.append(',');
        sb.append("year");
        sb.append('=');
        sb.append(((this.year == null)?"<null>":this.year));
        sb.append(',');
        sb.append("mileage");
        sb.append('=');
        sb.append(((this.mileage == null)?"<null>":this.mileage));
        sb.append(',');
        sb.append("additionalProperties");
        sb.append('=');
        sb.append(((this.additionalProperties == null)?"<null>":this.additionalProperties));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.trim == null)? 0 :this.trim.hashCode()));
        result = ((result* 31)+((this.color == null)? 0 :this.color.hashCode()));
        result = ((result* 31)+((this.year == null)? 0 :this.year.hashCode()));
        result = ((result* 31)+((this.model == null)? 0 :this.model.hashCode()));
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.make == null)? 0 :this.make.hashCode()));
        result = ((result* 31)+((this.mileage == null)? 0 :this.mileage.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Car) == false) {
            return false;
        }
        Car rhs = ((Car) other);
        return ((((((((this.trim == rhs.trim)||((this.trim!= null)&&this.trim.equals(rhs.trim)))&&((this.color == rhs.color)||((this.color!= null)&&this.color.equals(rhs.color))))&&((this.year == rhs.year)||((this.year!= null)&&this.year.equals(rhs.year))))&&((this.model == rhs.model)||((this.model!= null)&&this.model.equals(rhs.model))))&&((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties))))&&((this.make == rhs.make)||((this.make!= null)&&this.make.equals(rhs.make))))&&((this.mileage == rhs.mileage)||((this.mileage!= null)&&this.mileage.equals(rhs.mileage))));
    }

}

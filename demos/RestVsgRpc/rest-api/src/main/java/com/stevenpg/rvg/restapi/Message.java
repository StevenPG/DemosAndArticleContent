
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
    "car",
    "driver"
})
@Generated("jsonschema2pojo")
public class Message {

    @JsonProperty("car")
    private Car car;
    @JsonProperty("driver")
    private Driver driver;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("car")
    public Car getCar() {
        return car;
    }

    @JsonProperty("car")
    public void setCar(Car car) {
        this.car = car;
    }

    public Message withCar(Car car) {
        this.car = car;
        return this;
    }

    @JsonProperty("driver")
    public Driver getDriver() {
        return driver;
    }

    @JsonProperty("driver")
    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Message withDriver(Driver driver) {
        this.driver = driver;
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

    public Message withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Message.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("car");
        sb.append('=');
        sb.append(((this.car == null)?"<null>":this.car));
        sb.append(',');
        sb.append("driver");
        sb.append('=');
        sb.append(((this.driver == null)?"<null>":this.driver));
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
        result = ((result* 31)+((this.additionalProperties == null)? 0 :this.additionalProperties.hashCode()));
        result = ((result* 31)+((this.driver == null)? 0 :this.driver.hashCode()));
        result = ((result* 31)+((this.car == null)? 0 :this.car.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Message) == false) {
            return false;
        }
        Message rhs = ((Message) other);
        return ((((this.additionalProperties == rhs.additionalProperties)||((this.additionalProperties!= null)&&this.additionalProperties.equals(rhs.additionalProperties)))&&((this.driver == rhs.driver)||((this.driver!= null)&&this.driver.equals(rhs.driver))))&&((this.car == rhs.car)||((this.car!= null)&&this.car.equals(rhs.car))));
    }

}

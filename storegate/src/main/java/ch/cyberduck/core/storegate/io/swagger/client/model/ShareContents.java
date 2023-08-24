/*
 * Storegate api v4.2
 * No description provided (generated by Swagger Codegen https://github.com/swagger-api/swagger-codegen)
 *
 * OpenAPI spec version: v4.2
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package ch.cyberduck.core.storegate.io.swagger.client.model;

import java.util.Objects;
import java.util.Arrays;
import ch.cyberduck.core.storegate.io.swagger.client.model.Share;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains a list of enhanced shared pathResourceItems.
 */
@ApiModel(description = "Contains a list of enhanced shared pathResourceItems.")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2023-08-24T11:36:23.792+02:00")
public class ShareContents {
  @JsonProperty("totalRowCount")
  private Integer totalRowCount = null;

  @JsonProperty("shares")
  private List<Share> shares = null;

  public ShareContents totalRowCount(Integer totalRowCount) {
    this.totalRowCount = totalRowCount;
    return this;
  }

   /**
   * Total number of item.
   * @return totalRowCount
  **/
  @ApiModelProperty(value = "Total number of item.")
  public Integer getTotalRowCount() {
    return totalRowCount;
  }

  public void setTotalRowCount(Integer totalRowCount) {
    this.totalRowCount = totalRowCount;
  }

  public ShareContents shares(List<Share> shares) {
    this.shares = shares;
    return this;
  }

  public ShareContents addSharesItem(Share sharesItem) {
    if (this.shares == null) {
      this.shares = new ArrayList<>();
    }
    this.shares.add(sharesItem);
    return this;
  }

   /**
   * The list of items.
   * @return shares
  **/
  @ApiModelProperty(value = "The list of items.")
  public List<Share> getShares() {
    return shares;
  }

  public void setShares(List<Share> shares) {
    this.shares = shares;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ShareContents shareContents = (ShareContents) o;
    return Objects.equals(this.totalRowCount, shareContents.totalRowCount) &&
        Objects.equals(this.shares, shareContents.shares);
  }

  @Override
  public int hashCode() {
    return Objects.hash(totalRowCount, shares);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ShareContents {\n");
    
    sb.append("    totalRowCount: ").append(toIndentedString(totalRowCount)).append("\n");
    sb.append("    shares: ").append(toIndentedString(shares)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}


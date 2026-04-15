package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddressDto {
    private Long id;
    private String addressType;
    private String receiverName;
    private String receiverPhone;
    private String streetAddress;
    private String ward;
    private String district;
    private String city;
    private Boolean isDefault;
}

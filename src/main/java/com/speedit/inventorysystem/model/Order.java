package com.speedit.inventorysystem.model;

import com.speedit.inventorysystem.enums.OrderStatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_order")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Integer orderId;

    @NotBlank
    private String deliveryLocation;

    @NotNull
    @Enumerated(EnumType.STRING)
    private OrderStatusEnum orderStatus;

    // The employee who is in charge of the order
    @ManyToOne @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    /** who placed the order */
    @ManyToOne @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @NotNull @PositiveOrZero
    private Long totalPrice;

//    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
//    private List<TransactionLog> transactionLogs = new ArrayList<>();
}


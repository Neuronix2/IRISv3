package ru.iris.models.database;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ru.iris.models.protocol.enums.DeviceType;
import ru.iris.models.protocol.enums.SourceProtocol;
import ru.iris.models.protocol.enums.State;

import javax.persistence.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date date;

    private String channel;

    private String humanReadable;
    private String manufacturer;
    private String productName;

    @Enumerated(EnumType.STRING)
    private State state;

    @Enumerated(EnumType.STRING)
    private SourceProtocol source;

    @Enumerated(EnumType.STRING)
    private DeviceType type;

    @ManyToOne
    private Zone zone;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "device")
    @OrderBy("name ASC")
    @MapKey(name = "name")
    @JsonManagedReference
    private Map<String, DeviceValue> values = new ConcurrentHashMap<>();

    // State will be get in runtime runtime
}
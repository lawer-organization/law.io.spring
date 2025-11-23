package bj.gouv.sgg.model;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signatory {
    @Expose
    private String role;
    
    @Expose
    private String name;
    
    @Expose(serialize = false, deserialize = false)
    private LocalDate mandateStart;
    
    @Expose(serialize = false, deserialize = false)
    private LocalDate mandateEnd;
}

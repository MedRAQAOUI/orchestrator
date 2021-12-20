package fr.donnees.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@AllArgsConstructor
@NoArgsConstructor
@Getter 
@Setter
@ToString
public class RestConnection {
	
	String urlBase;
	
	String username;
	String password;
	
	

}

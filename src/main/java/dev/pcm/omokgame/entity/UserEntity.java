package dev.pcm.omokgame.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;



/*
* @NoArgsConstructor : 파라미터가 없는 기본 생성자를 자동으로 생성
* @AllArgsConstructor :  모든 필드를 포함한 생성자를 자동으로 생성. 주로 객체를 생성할 때 모든 필드 값을 한 번에 설정하는 편의성을 제공
* @Id: 엔터티의 기본 키
* @GeneratedValue: 기본 키의 생성 전략을 지정.
*                   여기서는 IDENTITY를 사용하여 자동 증가되는 기본 키를 사용
* @Column: 엔터티의 필드와 데이터베이스 테이블의 컬럼 간의 매핑을 지정.
*           여기서는 각각의 속성을 나타내는 컬럼에 대한 설정이 포함되어 있음.
* */

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @Column(unique = true, nullable = false)
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = true)
    private String sessionid;
}
